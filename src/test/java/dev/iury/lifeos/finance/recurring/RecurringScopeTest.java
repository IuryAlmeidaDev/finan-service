package dev.iury.lifeos.finance.recurring;

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
import dev.iury.lifeos.finance.model.RecurrenceScope;
import dev.iury.lifeos.finance.model.RecurringFrequency;
import dev.iury.lifeos.finance.model.RecurringRule;
import dev.iury.lifeos.finance.model.TransactionType;
import dev.iury.lifeos.finance.repository.AccountRepository;
import dev.iury.lifeos.finance.repository.CategoryRepository;
import dev.iury.lifeos.finance.repository.RecurringRuleRepository;
import dev.iury.lifeos.finance.repository.TransactionRepository;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class RecurringScopeTest {

    @Inject RecurringService recurringService;
    @Inject TransactionRepository transactions;
    @Inject AccountRepository accounts;
    @Inject CategoryRepository categories;
    @Inject RecurringRuleRepository rules;
    @Inject EntityManager em;

    private Account account;
    private Category expenseCategory;
    private Category incomeCategory;

    @BeforeEach
    @Transactional
    void setup() {
        cleanAll();

        account = new Account();
        account.name = "Test Account";
        account.type = AccountType.CHECKING;
        account.initialBalance = BigDecimal.ZERO;
        account.initialBalanceDate = LocalDate.now();
        accounts.persist(account);

        expenseCategory = new Category();
        expenseCategory.name = "Bills";
        expenseCategory.type = CategoryType.EXPENSE;
        expenseCategory.color = "#ff5733";
        expenseCategory.iconSlug = "bolt";
        categories.persist(expenseCategory);

        incomeCategory = new Category();
        incomeCategory.name = "Salary";
        incomeCategory.type = CategoryType.INCOME;
        incomeCategory.color = "#33ff57";
        incomeCategory.iconSlug = "wallet";
        categories.persist(incomeCategory);
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
        em.createQuery("delete from InstallmentGroup").executeUpdate();
        rules.deleteAll();
        accounts.deleteAll();
        categories.delete("parentCategory is not null and system = false");
        categories.delete("system = false");
    }

    // ────────────────────────────────────────────────────────────────
    // CREATE RULE
    // ────────────────────────────────────────────────────────────────

    @Test
    @Transactional
    void shouldCreateMonthlyExpenseRule() {
        RecurringRule rule = recurringService.createRule(
                account.id, TransactionType.EXPENSE, new BigDecimal("100.00"),
                expenseCategory.id, "Internet", RecurringFrequency.MONTHLY,
                10, null, LocalDate.of(2026, 1, 10), null, false);

        assertThat(rule.id).isNotNull();
        assertThat(rule.active).isTrue();
        assertThat(rule.frequency).isEqualTo(RecurringFrequency.MONTHLY);
        assertThat(rule.dayOfMonth).isEqualTo(10);
        assertThat(rule.amount).isEqualByComparingTo("100.00");
    }

    @Test
    @Transactional
    void shouldRejectTransferTypeForRecurring() {
        assertThatThrownBy(() -> recurringService.createRule(
                account.id, TransactionType.TRANSFER, new BigDecimal("100.00"),
                expenseCategory.id, "Bad", RecurringFrequency.MONTHLY,
                10, null, LocalDate.now(), null, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("INCOME or EXPENSE");
    }

    // ────────────────────────────────────────────────────────────────
    // DEACTIVATE RULE
    // ────────────────────────────────────────────────────────────────

    @Test
    @Transactional
    void shouldDeactivateRule() {
        RecurringRule rule = createTestRule();
        recurringService.deactivateRule(rule.id);
        em.flush();
        em.clear();

        RecurringRule reloaded = rules.findById(rule.id);
        assertThat(reloaded.active).isFalse();
    }

    @Test
    @Transactional
    void shouldRejectDeactivateOfInactiveRule() {
        RecurringRule rule = createTestRule();
        recurringService.deactivateRule(rule.id);

        assertThatThrownBy(() -> recurringService.deactivateRule(rule.id))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already inactive");
    }

    @Test
    @Transactional
    void shouldGenerateOccurrencesThroughEndDateAndDeactivateRule() {
        RecurringRule rule = recurringService.createRule(
                account.id, TransactionType.EXPENSE, new BigDecimal("100.00"),
                expenseCategory.id, "Internet", RecurringFrequency.MONTHLY,
                10, null, LocalDate.of(2026, 1, 10), LocalDate.of(2026, 3, 10), true);

        recurringService.generateOccurrences(rule.id, LocalDate.of(2026, 4, 1));
        em.flush();
        em.clear();

        List<FinancialTransaction> occurrences = recurringService.listOccurrences(rule.id);
        assertThat(occurrences).hasSize(3);
        assertThat(occurrences).extracting(tx -> tx.date)
                .containsExactly(LocalDate.of(2026, 1, 10), LocalDate.of(2026, 2, 10), LocalDate.of(2026, 3, 10));
        assertThat(occurrences).allSatisfy(tx -> assertThat(tx.paid).isTrue());
        assertThat(rules.findById(rule.id).lastGeneratedDate).isEqualTo(LocalDate.of(2026, 3, 10));
        assertThat(rules.findById(rule.id).active).isFalse();
    }

    // ────────────────────────────────────────────────────────────────
    // UPDATE OCCURRENCE — ONLY_THIS
    // ────────────────────────────────────────────────────────────────

    @Test
    @Transactional
    void shouldUpdateOnlyThisOccurrence() {
        RecurringRule rule = createTestRule();
        List<FinancialTransaction> txs = createOccurrences(rule, 3);

        recurringService.updateOccurrence(txs.get(1).id, RecurrenceScope.ONLY_THIS,
                new RecurringUpdateCommand(new BigDecimal("200.00"), "Updated", null, null));

        em.flush();
        em.clear();

        List<FinancialTransaction> reloaded = recurringService.listOccurrences(rule.id);
        assertThat(reloaded.get(0).amount).isEqualByComparingTo("100.00");
        assertThat(reloaded.get(1).amount).isEqualByComparingTo("200.00");
        assertThat(reloaded.get(1).description).isEqualTo("Updated");
        assertThat(reloaded.get(2).amount).isEqualByComparingTo("100.00");
    }

    // ────────────────────────────────────────────────────────────────
    // UPDATE OCCURRENCE — THIS_AND_FUTURE
    // ────────────────────────────────────────────────────────────────

    @Test
    @Transactional
    void shouldMoveThisAndFutureOccurrencesToNewRule() {
        RecurringRule rule = createTestRule();
        List<FinancialTransaction> txs = createOccurrences(rule, 4);

        recurringService.updateOccurrence(txs.get(1).id, RecurrenceScope.THIS_AND_FUTURE,
                new RecurringUpdateCommand(new BigDecimal("300.00"), null, null, null));

        em.flush();
        em.clear();

        List<FinancialTransaction> originalOccurrences = recurringService.listOccurrences(rule.id);
        assertThat(originalOccurrences).hasSize(1);
        assertThat(originalOccurrences.get(0).amount).isEqualByComparingTo("100.00");

        FinancialTransaction moved = transactions.findById(txs.get(1).id);
        assertThat(moved.recurringRule.id).isNotEqualTo(rule.id);
        assertThat(moved.amount).isEqualByComparingTo("300.00");
    }

    @Test
    @Transactional
    void shouldNeverUpdatePaidOccurrence() {
        RecurringRule rule = createTestRule();
        List<FinancialTransaction> txs = createOccurrences(rule, 3);
        txs.get(1).paid = true;

        recurringService.updateOccurrence(txs.get(1).id, RecurrenceScope.ALL,
                new RecurringUpdateCommand(new BigDecimal("300.00"), "Updated", null, false));
        em.flush();
        em.clear();

        FinancialTransaction paid = transactions.findById(txs.get(1).id);
        assertThat(paid.amount).isEqualByComparingTo("100.00");
        assertThat(paid.description).isEqualTo("Internet");
        assertThat(paid.paid).isTrue();
    }

    // ────────────────────────────────────────────────────────────────
    // UPDATE OCCURRENCE — ALL (also updates rule)
    // ────────────────────────────────────────────────────────────────

    @Test
    @Transactional
    void shouldUpdateAllOccurrencesAndRule() {
        RecurringRule rule = createTestRule();
        List<FinancialTransaction> txs = createOccurrences(rule, 3);

        recurringService.updateOccurrence(txs.get(2).id, RecurrenceScope.ALL,
                new RecurringUpdateCommand(new BigDecimal("500.00"), "New Desc", null, null));

        em.flush();
        em.clear();

        List<FinancialTransaction> reloaded = recurringService.listOccurrences(rule.id);
        for (FinancialTransaction t : reloaded) {
            assertThat(t.amount).isEqualByComparingTo("500.00");
            assertThat(t.description).isEqualTo("New Desc");
        }

        RecurringRule reloadedRule = rules.findById(rule.id);
        assertThat(reloadedRule.amount).isEqualByComparingTo("500.00");
        assertThat(reloadedRule.description).isEqualTo("New Desc");
    }

    // ────────────────────────────────────────────────────────────────
    // DELETE OCCURRENCE — ONLY_THIS
    // ────────────────────────────────────────────────────────────────

    @Test
    @Transactional
    void shouldDeleteOnlyThisOccurrence() {
        RecurringRule rule = createTestRule();
        List<FinancialTransaction> txs = createOccurrences(rule, 3);

        recurringService.deleteOccurrence(txs.get(1).id, RecurrenceScope.ONLY_THIS);
        em.flush();
        em.clear();

        List<FinancialTransaction> remaining = recurringService.listOccurrences(rule.id);
        assertThat(remaining).hasSize(2);
        assertThat(remaining.get(0).recurringInstanceIndex).isEqualTo(1);
        assertThat(remaining.get(1).recurringInstanceIndex).isEqualTo(3);

        // Rule is still active
        assertThat(rules.findById(rule.id).active).isTrue();
    }

    // ────────────────────────────────────────────────────────────────
    // DELETE OCCURRENCE — THIS_AND_FUTURE (deactivates rule)
    // ────────────────────────────────────────────────────────────────

    @Test
    @Transactional
    void shouldDeleteThisAndFutureAndDeactivateRule() {
        RecurringRule rule = createTestRule();
        List<FinancialTransaction> txs = createOccurrences(rule, 4);

        recurringService.deleteOccurrence(txs.get(2).id, RecurrenceScope.THIS_AND_FUTURE);
        em.flush();
        em.clear();

        List<FinancialTransaction> remaining = recurringService.listOccurrences(rule.id);
        assertThat(remaining).hasSize(2); // indices 0, 1

        assertThat(rules.findById(rule.id).active).isFalse();
    }

    // ────────────────────────────────────────────────────────────────
    // DELETE OCCURRENCE — ALL (deactivates rule)
    // ────────────────────────────────────────────────────────────────

    @Test
    @Transactional
    void shouldDeleteAllOccurrencesAndDeactivateRule() {
        RecurringRule rule = createTestRule();
        createOccurrences(rule, 3);

        List<FinancialTransaction> all = recurringService.listOccurrences(rule.id);
        recurringService.deleteOccurrence(all.get(0).id, RecurrenceScope.ALL);
        em.flush();
        em.clear();

        assertThat(recurringService.listOccurrences(rule.id)).isEmpty();
        assertThat(rules.findById(rule.id).active).isFalse();
    }

    // ────────────────────────────────────────────────────────────────
    // Helpers
    // ────────────────────────────────────────────────────────────────

    private RecurringRule createTestRule() {
        return recurringService.createRule(
                account.id, TransactionType.EXPENSE, new BigDecimal("100.00"),
                expenseCategory.id, "Internet", RecurringFrequency.MONTHLY,
                10, null, LocalDate.of(2026, 1, 10), null, false);
    }

    /**
     * Simulates N occurrences of a rule by creating transactions with sequential instance indices.
     */
    private List<FinancialTransaction> createOccurrences(RecurringRule rule, int count) {
        for (int i = 1; i <= count; i++) {
            FinancialTransaction tx = new FinancialTransaction();
            tx.account = rule.account;
            tx.type = rule.type;
            tx.amount = rule.amount;
            tx.date = rule.startDate.plusMonths(i - 1);
            tx.category = rule.category;
            tx.description = rule.description;
            tx.paid = false;
            tx.recurringRule = rule;
            tx.recurringInstanceIndex = i;
            transactions.persist(tx);
        }
        em.flush();
        return recurringService.listOccurrences(rule.id);
    }
}
