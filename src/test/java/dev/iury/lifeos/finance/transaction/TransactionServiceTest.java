package dev.iury.lifeos.finance.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.iury.lifeos.finance.account.AccountService;
import dev.iury.lifeos.finance.model.Account;
import dev.iury.lifeos.finance.model.AccountType;
import dev.iury.lifeos.finance.model.Category;
import dev.iury.lifeos.finance.model.CategoryType;
import dev.iury.lifeos.finance.model.FinancialTransaction;
import dev.iury.lifeos.finance.model.TransactionType;
import dev.iury.lifeos.finance.repository.AccountRepository;
import dev.iury.lifeos.finance.repository.BudgetRepository;
import dev.iury.lifeos.finance.repository.CategoryRepository;
import dev.iury.lifeos.finance.repository.IncomeGoalRepository;
import dev.iury.lifeos.finance.repository.TransactionRepository;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * Each test is wrapped in @Transactional (commits). An @AfterEach teardown
 * cleans all data, guaranteeing no leakage to other test classes.
 */
@QuarkusTest
class TransactionServiceTest {

    @Inject TransactionService transactionService;
    @Inject AccountService accountService;
    @Inject AccountRepository accounts;
    @Inject BudgetRepository budgets;
    @Inject IncomeGoalRepository incomeGoals;
    @Inject CategoryRepository categories;
    @Inject TransactionRepository transactions;

    private Account account;
    private Account otherAccount;
    private Category incomeCat;
    private Category expenseCat;

    @BeforeEach
    @Transactional
    void setup() {
        cleanAll();

        account = new Account();
        account.name = "Main";
        account.type = AccountType.CHECKING;
        account.initialBalance = new BigDecimal("0.00");
        account.initialBalanceDate = LocalDate.now();
        account.includeInTotal = true;
        accounts.persist(account);

        otherAccount = new Account();
        otherAccount.name = "Other";
        otherAccount.type = AccountType.CHECKING;
        otherAccount.initialBalance = new BigDecimal("0.00");
        otherAccount.initialBalanceDate = LocalDate.now();
        otherAccount.includeInTotal = true;
        accounts.persist(otherAccount);

        incomeCat = new Category();
        incomeCat.name = "Salary";
        incomeCat.type = CategoryType.INCOME;
        categories.persist(incomeCat);

        expenseCat = new Category();
        expenseCat.name = "Food";
        expenseCat.type = CategoryType.EXPENSE;
        categories.persist(expenseCat);
    }

    @AfterEach
    @Transactional
    void teardown() {
        cleanAll();
    }

    private void cleanAll() {
        transactions.deleteAll();
        budgets.deleteAll();
        incomeGoals.deleteAll();
        accounts.deleteAll();
        categories.delete("system = false");
    }

    // ────────────────────────────────────────────────────────────────
    // CREATE — valid cases
    // ────────────────────────────────────────────────────────────────

    @Test
    @Transactional
    void shouldCreateIncomeTransaction() {
        FinancialTransaction tx = transactionService.create(
                account.id, TransactionType.INCOME, new BigDecimal("500.00"),
                LocalDate.now(), incomeCat.id, "Freelance", false, false);

        assertThat(tx.id).isNotNull();
        assertThat(tx.type).isEqualTo(TransactionType.INCOME);
        assertThat(tx.amount).isEqualByComparingTo("500.00");
        assertThat(tx.paid).isFalse();
        assertThat(tx.deletedAt).isNull();
    }

    @Test
    @Transactional
    void shouldCreateExpenseTransaction() {
        FinancialTransaction tx = transactionService.create(
                account.id, TransactionType.EXPENSE, new BigDecimal("150.00"),
                LocalDate.now(), expenseCat.id, "Groceries", false, false);

        assertThat(tx.id).isNotNull();
        assertThat(tx.type).isEqualTo(TransactionType.EXPENSE);
        assertThat(tx.amount).isEqualByComparingTo("150.00");
    }

    @Test
    @Transactional
    void shouldCreateTransferTransaction() {
        FinancialTransaction tx = transactionService.createTransfer(
                account.id, otherAccount.id, new BigDecimal("200.00"),
                LocalDate.now(), "Monthly transfer", false);

        assertThat(tx.id).isNotNull();
        assertThat(tx.type).isEqualTo(TransactionType.TRANSFER);
        assertThat(tx.account.id).isEqualTo(account.id);
        assertThat(tx.destinationAccount.id).isEqualTo(otherAccount.id);
        assertThat(tx.amount).isEqualByComparingTo("200.00");
    }

    // ────────────────────────────────────────────────────────────────
    // CREATE — validation failures (these don't commit any data)
    // ────────────────────────────────────────────────────────────────

    @Test
    void shouldRejectTransactionWithArchivedAccount() {
        // Mark account archived in its own transaction
        archiveAccount(account.id);

        assertThatThrownBy(() -> createExpenseNoTx(account.id, "50.00", "Blocked"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("archived");
    }

    @Test
    void shouldRejectIncomeWithExpenseCategory() {
        assertThatThrownBy(() -> createWithWrongCategory(TransactionType.INCOME, expenseCat.id))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("category");
    }

    @Test
    void shouldRejectExpenseWithIncomeCategory() {
        assertThatThrownBy(() -> createWithWrongCategory(TransactionType.EXPENSE, incomeCat.id))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("category");
    }

    @Test
    void shouldRejectZeroAmount() {
        assertThatThrownBy(() -> createWithAmount(BigDecimal.ZERO))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("amount");
    }

    @Test
    void shouldRejectNegativeAmount() {
        assertThatThrownBy(() -> createWithAmount(new BigDecimal("-10.00")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("amount");
    }

    @Test
    void shouldRejectTransferToSameAccount() {
        assertThatThrownBy(() -> transactionService.createTransfer(
                account.id, account.id, new BigDecimal("100.00"),
                LocalDate.now(), "Self transfer", false))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("destination");
    }

    @Test
    void shouldRejectTransferWithNullDestination() {
        assertThatThrownBy(() -> transactionService.createTransfer(
                account.id, null, new BigDecimal("100.00"),
                LocalDate.now(), "No destination", false))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("destination");
    }

    // ────────────────────────────────────────────────────────────────
    // PAY / UNPAY
    // ────────────────────────────────────────────────────────────────

    @Test
    @Transactional
    void shouldPayTransaction() {
        FinancialTransaction tx = transactionService.create(
                account.id, TransactionType.INCOME, new BigDecimal("300.00"),
                LocalDate.now(), incomeCat.id, "Pay me", false, false);

        assertThat(tx.paid).isFalse();

        transactionService.pay(tx.id);

        FinancialTransaction reloaded = transactions.findById(tx.id);
        assertThat(reloaded.paid).isTrue();
    }

    @Test
    @Transactional
    void shouldUnpayTransaction() {
        FinancialTransaction tx = transactionService.create(
                account.id, TransactionType.EXPENSE, new BigDecimal("80.00"),
                LocalDate.now(), expenseCat.id, "Paid expense", true, false);

        assertThat(tx.paid).isTrue();

        transactionService.unpay(tx.id);

        FinancialTransaction reloaded = transactions.findById(tx.id);
        assertThat(reloaded.paid).isFalse();
    }

    // ────────────────────────────────────────────────────────────────
    // UPDATE
    // ────────────────────────────────────────────────────────────────

    @Test
    @Transactional
    void shouldUpdateTransactionFields() {
        FinancialTransaction tx = transactionService.create(
                account.id, TransactionType.EXPENSE, new BigDecimal("100.00"),
                LocalDate.now(), expenseCat.id, "Original", false, false);

        transactionService.update(tx.id, new BigDecimal("200.00"),
                LocalDate.now().plusDays(1), expenseCat.id, "Updated", false, false);

        FinancialTransaction reloaded = transactions.findById(tx.id);
        assertThat(reloaded.amount).isEqualByComparingTo("200.00");
        assertThat(reloaded.description).isEqualTo("Updated");
        assertThat(reloaded.date).isEqualTo(LocalDate.now().plusDays(1));
    }

    // ────────────────────────────────────────────────────────────────
    // SOFT DELETE
    // ────────────────────────────────────────────────────────────────

    @Test
    @Transactional
    void shouldSoftDeleteTransaction() {
        FinancialTransaction tx = transactionService.create(
                account.id, TransactionType.EXPENSE, new BigDecimal("50.00"),
                LocalDate.now(), expenseCat.id, "To delete", false, false);

        assertThat(tx.deletedAt).isNull();

        transactionService.delete(tx.id);

        FinancialTransaction reloaded = transactions.findById(tx.id);
        assertThat(reloaded.deletedAt).isNotNull();
    }

    @Test
    @Transactional
    void shouldRejectOperationOnDeletedTransaction() {
        FinancialTransaction tx = transactionService.create(
                account.id, TransactionType.EXPENSE, new BigDecimal("50.00"),
                LocalDate.now(), expenseCat.id, "Deleted", false, false);
        transactionService.delete(tx.id);

        assertThatThrownBy(() -> transactionService.pay(tx.id))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("deleted");
    }

    // ────────────────────────────────────────────────────────────────
    // BALANCE ADJUSTMENT
    // ────────────────────────────────────────────────────────────────

    @Test
    @Transactional
    void shouldNotCreateBalanceAdjustmentWhenDifferenceIsZero() {
        long countBefore = transactions.count();

        transactionService.adjust(account.id, BigDecimal.ZERO);

        long countAfter = transactions.count();
        assertThat(countAfter).isEqualTo(countBefore);
    }

    @Test
    @Transactional
    void shouldCreateNegativeAdjustmentWhenCurrentBalanceIsHigher() {
        transactionService.create(account.id, TransactionType.INCOME,
                new BigDecimal("100.00"), LocalDate.now(), incomeCat.id, "Income", true, false);

        transactionService.adjust(account.id, new BigDecimal("60.00"));

        FinancialTransaction adj = transactions.find(
                "type = ?1 and deletedAt is null and account.id = ?2",
                TransactionType.BALANCE_ADJUSTMENT, account.id)
            .stream()
            .filter(t -> t.destinationAccount == null)
            .findFirst()
            .orElseThrow();

        assertThat(adj.amount).isEqualByComparingTo("40.00");
        assertThat(adj.paid).isTrue();
    }

    @Test
    @Transactional
    void shouldCreatePositiveAdjustmentWhenCurrentBalanceIsLower() {
        transactionService.adjust(account.id, new BigDecimal("50.00"));

        FinancialTransaction adj = transactions.find(
                "type = ?1 and deletedAt is null and destinationAccount.id = ?2",
                TransactionType.BALANCE_ADJUSTMENT, account.id)
            .firstResult();

        assertThat(adj).isNotNull();
        assertThat(adj.amount).isEqualByComparingTo("50.00");
        assertThat(adj.paid).isTrue();
    }

    // ────────────────────────────────────────────────────────────────
    // AccountService — archive / delete
    // ────────────────────────────────────────────────────────────────

    @Test
    @Transactional
    void shouldArchiveAccountWhenBalanceIsZero() {
        accountService.archive(account.id);

        Account reloaded = accounts.findById(account.id);
        assertThat(reloaded.archived).isTrue();
    }

    @Test
    @Transactional
    void shouldRejectArchiveWhenBalanceIsNotZero() {
        transactionService.create(account.id, TransactionType.INCOME,
                new BigDecimal("100.00"), LocalDate.now(), incomeCat.id, "Income", true, false);

        assertThatThrownBy(() -> accountService.archive(account.id))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("balance");
    }

    @Test
    @Transactional
    void shouldDeleteArchivedAccountWithCorrectConfirmation() {
        accountService.archive(account.id);

        accountService.delete(account.id, "EXCLUIR");

        assertThat(accounts.findById(account.id)).isNull();
    }

    @Test
    void shouldRejectDeleteWithWrongConfirmation() {
        assertThatThrownBy(() -> accountService.delete(account.id, "DELETE"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectDeleteOfNonArchivedAccount() {
        assertThatThrownBy(() -> accountService.delete(account.id, "EXCLUIR"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("archived");
    }

    // ────────────────────────────────────────────────────────────────
    // BALANCE ADJUSTMENT — type restrictions
    // ────────────────────────────────────────────────────────────────

    @Test
    void shouldRejectBalanceAdjustmentThroughGenericCreate() {
        assertThatThrownBy(() -> transactionService.create(
                account.id, TransactionType.BALANCE_ADJUSTMENT, new BigDecimal("50.00"),
                LocalDate.now(), null, "Hack", false, false))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("BALANCE_ADJUSTMENT");
    }

    // ────────────────────────────────────────────────────────────────
    // FIND
    // ────────────────────────────────────────────────────────────────

    @Test
    @Transactional
    void shouldFindTransactionById() {
        FinancialTransaction tx = transactionService.create(
                account.id, TransactionType.INCOME, new BigDecimal("100.00"),
                LocalDate.now(), incomeCat.id, "Find me", false, false);

        FinancialTransaction found = transactionService.findById(tx.id);
        assertThat(found.id).isEqualTo(tx.id);
    }

    @Test
    void shouldThrowWhenTransactionNotFound() {
        assertThatThrownBy(() -> transactionService.findById(UUID.randomUUID()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("not found");
    }

    // ────────────────────────────────────────────────────────────────
    // Private helpers — each wraps a single call in its own tx
    // so exceptions don't taint the caller's transaction context
    // ────────────────────────────────────────────────────────────────

    @Transactional
    void archiveAccount(UUID id) {
        accounts.getEntityManager().createQuery(
                "update Account a set a.archived = true where a.id = :id")
            .setParameter("id", id)
            .executeUpdate();
    }

    @Transactional
    void createExpenseNoTx(UUID accountId, String amount, String description) {
        transactionService.create(accountId, TransactionType.EXPENSE,
                new BigDecimal(amount), LocalDate.now(), expenseCat.id, description, false, false);
    }

    @Transactional
    void createWithWrongCategory(TransactionType type, UUID wrongCategoryId) {
        transactionService.create(account.id, type, new BigDecimal("50.00"),
                LocalDate.now(), wrongCategoryId, "Wrong cat", false, false);
    }

    @Transactional
    void createWithAmount(BigDecimal amount) {
        transactionService.create(account.id, TransactionType.EXPENSE, amount,
                LocalDate.now(), expenseCat.id, "Bad amount", false, false);
    }
}
