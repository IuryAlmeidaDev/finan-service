package dev.iury.lifeos.finance.installment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import dev.iury.lifeos.finance.model.Account;
import dev.iury.lifeos.finance.model.AccountType;
import dev.iury.lifeos.finance.model.Category;
import dev.iury.lifeos.finance.model.CategoryType;
import dev.iury.lifeos.finance.model.FinancialTransaction;
import dev.iury.lifeos.finance.model.InstallmentGroup;
import dev.iury.lifeos.finance.model.InstallmentStatus;
import dev.iury.lifeos.finance.repository.AccountRepository;
import dev.iury.lifeos.finance.repository.CategoryRepository;
import dev.iury.lifeos.finance.repository.InstallmentGroupRepository;
import dev.iury.lifeos.finance.repository.TransactionRepository;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class InstallmentIntegrationTest {

    @Inject InstallmentService installmentService;
    @Inject TransactionRepository transactions;
    @Inject AccountRepository accounts;
    @Inject CategoryRepository categories;
    @Inject InstallmentGroupRepository groups;
    @Inject EntityManager em;

    private Account account;
    private Category expenseCategory;

    @BeforeEach
    @Transactional
    void setup() {
        cleanAll();

        account = new Account();
        account.name = "Test Card";
        account.type = AccountType.CHECKING;
        account.initialBalance = BigDecimal.ZERO;
        account.initialBalanceDate = LocalDate.now();
        accounts.persist(account);

        expenseCategory = new Category();
        expenseCategory.name = "Shopping";
        expenseCategory.type = CategoryType.EXPENSE;
        expenseCategory.color = "#ff5733";
        expenseCategory.iconSlug = "cart";
        categories.persist(expenseCategory);
    }

    @AfterEach
    @Transactional
    void teardown() {
        cleanAll();
    }

    private void cleanAll() {
        transactions.deleteAll();
        em.createQuery("delete from Budget").executeUpdate();
        em.createQuery("delete from IncomeGoal").executeUpdate();
        groups.deleteAll();
        accounts.deleteAll();
        categories.delete("parentCategory is not null and system = false");
        categories.delete("system = false");
    }

    // ────────────────────────────────────────────────────────────────
    // CREATE — 12x happy path
    // ────────────────────────────────────────────────────────────────

    @Test
    @Transactional
    void shouldCreateGroupWith12InstallmentTransactions() {
        LocalDate firstDate = LocalDate.of(2026, 2, 10);
        InstallmentGroup group = installmentService.create(
                account.id, expenseCategory.id,
                new BigDecimal("1200.00"), 12, firstDate, "Notebook");

        assertThat(group.id).isNotNull();
        assertThat(group.status).isEqualTo(InstallmentStatus.ACTIVE);
        assertThat(group.totalInstallments).isEqualTo(12);
        assertThat(group.totalAmount).isEqualByComparingTo("1200.00");

        List<FinancialTransaction> installments = installmentService.listByGroup(group.id);
        assertThat(installments).hasSize(12);

        BigDecimal sum = installments.stream()
                .map(tx -> tx.amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sum).isEqualByComparingTo("1200.00");

        for (int i = 0; i < 12; i++) {
            FinancialTransaction tx = installments.get(i);
            assertThat(tx.installmentNumber).isEqualTo(i + 1);
            assertThat(tx.totalInstallments).isEqualTo(12);
            assertThat(tx.date).isEqualTo(firstDate.plusMonths(i));
            assertThat(tx.description).isEqualTo("Notebook " + (i + 1) + "/12");
            assertThat(tx.paid).isFalse();
            assertThat(tx.installmentGroup.id).isEqualTo(group.id);
            assertThat(tx.category.id).isEqualTo(expenseCategory.id);
        }
    }

    // ────────────────────────────────────────────────────────────────
    // CREATE — without category (EXPENSE requires category per DB)
    // ────────────────────────────────────────────────────────────────

    @Test
    @Transactional
    void shouldRejectInstallmentWithoutCategoryForExpense() {
        assertThatThrownBy(() -> installmentService.create(
                account.id, null,
                new BigDecimal("200.00"), 2, LocalDate.now(), "No Category"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("category");
    }

    // ────────────────────────────────────────────────────────────────
    // CANCEL — preserves paid installments
    // ────────────────────────────────────────────────────────────────

    @Test
    @Transactional
    void shouldCancelGroupPreservingPaidInstallments() {
        InstallmentGroup group = installmentService.create(
                account.id, expenseCategory.id,
                new BigDecimal("300.00"), 3, LocalDate.now(), "Cancel Test");

        // Pay the first installment
        List<FinancialTransaction> installments = installmentService.listByGroup(group.id);
        installments.get(0).paid = true;
        em.flush();

        installmentService.cancel(group.id);
        em.flush();
        em.clear();

        // Group is canceled
        InstallmentGroup reloaded = groups.findById(group.id);
        assertThat(reloaded.status).isEqualTo(InstallmentStatus.CANCELED);

        // The paid installment is still alive
        List<FinancialTransaction> remaining = installmentService.listByGroup(group.id);
        assertThat(remaining).hasSize(1);
        assertThat(remaining.get(0).paid).isTrue();
        assertThat(remaining.get(0).installmentNumber).isEqualTo(1);
    }

    // ────────────────────────────────────────────────────────────────
    // CANCEL — already canceled
    // ────────────────────────────────────────────────────────────────

    @Test
    @Transactional
    void shouldRejectCancelOfAlreadyCanceledGroup() {
        InstallmentGroup group = installmentService.create(
                account.id, expenseCategory.id,
                new BigDecimal("100.00"), 2, LocalDate.now(), "Double Cancel");

        installmentService.cancel(group.id);

        assertThatThrownBy(() -> installmentService.cancel(group.id))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already canceled");
    }

    // ────────────────────────────────────────────────────────────────
    // VALIDATION — invalid account
    // ────────────────────────────────────────────────────────────────

    @Test
    @Transactional
    void shouldRejectInvalidAccount() {
        assertThatThrownBy(() -> installmentService.create(
                java.util.UUID.randomUUID(), null,
                new BigDecimal("100.00"), 2, LocalDate.now(), "Bad"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Account not found");
    }
}
