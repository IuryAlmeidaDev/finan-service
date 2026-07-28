package dev.iury.lifeos.finance.recurring;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import dev.iury.lifeos.finance.model.Account;
import dev.iury.lifeos.finance.model.Category;
import dev.iury.lifeos.finance.model.FinancialTransaction;
import dev.iury.lifeos.finance.model.RecurrenceScope;
import dev.iury.lifeos.finance.model.RecurringFrequency;
import dev.iury.lifeos.finance.model.RecurringRule;
import dev.iury.lifeos.finance.model.TransactionType;
import dev.iury.lifeos.finance.repository.AccountRepository;
import dev.iury.lifeos.finance.repository.CategoryRepository;
import dev.iury.lifeos.finance.repository.RecurringRuleRepository;
import dev.iury.lifeos.finance.repository.TransactionRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * Manages recurring transaction rules and scoped operations (ONLY_THIS, THIS_AND_FUTURE, ALL).
 */
@ApplicationScoped
public class RecurringService {

    @Inject RecurringRuleRepository rules;
    @Inject TransactionRepository transactions;
    @Inject AccountRepository accounts;
    @Inject CategoryRepository categories;
    @Inject RecurringDateCalculator dateCalculator;

    // ────────────────────────────────────────────────────────────────
    // CREATE RULE
    // ────────────────────────────────────────────────────────────────

    /**
     * Creates a new recurring rule.
     */
    @Transactional
    public RecurringRule createRule(
            UUID accountId,
            TransactionType type,
            BigDecimal amount,
            UUID categoryId,
            String description,
            RecurringFrequency frequency,
            Integer dayOfMonth,
            DayOfWeek dayOfWeek,
            LocalDate startDate,
            LocalDate endDate,
            boolean autoConfirm) {

        Account account = accounts.findById(accountId);
        if (account == null || account.archived) {
            throw new IllegalArgumentException("Account not found or archived: " + accountId);
        }

        if (type == TransactionType.TRANSFER || type == TransactionType.BALANCE_ADJUSTMENT) {
            throw new IllegalArgumentException("Recurring rules only support INCOME or EXPENSE");
        }

        Category category = categories.findById(categoryId);
        if (category == null) {
            throw new IllegalArgumentException("Category not found: " + categoryId);
        }

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }

        // Validate date parameters match frequency
        dateCalculator.nextDate(startDate, frequency, dayOfMonth, dayOfWeek);

        RecurringRule rule = new RecurringRule();
        rule.account = account;
        rule.type = type;
        rule.amount = amount;
        rule.category = category;
        rule.description = description;
        rule.frequency = frequency;
        rule.dayOfMonth = dayOfMonth;
        rule.dayOfWeek = dayOfWeek;
        rule.startDate = startDate;
        rule.endDate = endDate;
        rule.autoConfirm = autoConfirm;
        rule.active = true;
        rules.persist(rule);
        return rule;
    }

    // ────────────────────────────────────────────────────────────────
    // FIND / LIST
    // ────────────────────────────────────────────────────────────────

    public RecurringRule findRule(UUID ruleId) {
        RecurringRule rule = rules.findById(ruleId);
        if (rule == null) {
            throw new IllegalArgumentException("Recurring rule not found: " + ruleId);
        }
        return rule;
    }

    public List<RecurringRule> listActiveRules() {
        return rules.findActive();
    }

    public List<FinancialTransaction> listOccurrences(UUID ruleId) {
        return transactions.list(
                "recurringRule.id = ?1 and deletedAt is null order by recurringInstanceIndex", ruleId);
    }

    // ────────────────────────────────────────────────────────────────
    // DEACTIVATE RULE
    // ────────────────────────────────────────────────────────────────

    @Transactional
    public void deactivateRule(UUID ruleId) {
        RecurringRule rule = findRule(ruleId);
        if (!rule.active) {
            throw new IllegalStateException("Rule is already inactive: " + ruleId);
        }
        rule.active = false;
    }

    // ────────────────────────────────────────────────────────────────
    // UPDATE OCCURRENCE (scoped)
    // ────────────────────────────────────────────────────────────────

    /**
     * Updates a recurring transaction occurrence with the given scope:
     * <ul>
     *   <li>ONLY_THIS: updates only the specified transaction</li>
     *   <li>THIS_AND_FUTURE: updates this transaction and all future occurrences (by index)</li>
     *   <li>ALL: updates all occurrences of this rule</li>
     * </ul>
     */
    @Transactional
    public void updateOccurrence(UUID transactionId, RecurrenceScope scope, RecurringUpdateCommand command) {
        FinancialTransaction tx = findActiveTransaction(transactionId);
        if (tx.recurringRule == null) {
            throw new IllegalStateException("Transaction is not part of a recurring rule");
        }

        Category newCategory = null;
        if (command.categoryId() != null) {
            newCategory = categories.findById(command.categoryId());
            if (newCategory == null) {
                throw new IllegalArgumentException("Category not found: " + command.categoryId());
            }
        }

        switch (scope) {
            case ONLY_THIS -> applyUpdate(tx, command, newCategory);
            case THIS_AND_FUTURE -> {
                List<FinancialTransaction> futures = transactions.list(
                        "recurringRule.id = ?1 and deletedAt is null and recurringInstanceIndex >= ?2 order by recurringInstanceIndex",
                        tx.recurringRule.id, tx.recurringInstanceIndex);
                for (FinancialTransaction f : futures) {
                    applyUpdate(f, command, newCategory);
                }
            }
            case ALL -> {
                List<FinancialTransaction> all = transactions.list(
                        "recurringRule.id = ?1 and deletedAt is null order by recurringInstanceIndex",
                        tx.recurringRule.id);
                for (FinancialTransaction a : all) {
                    applyUpdate(a, command, newCategory);
                }
                // Also update the rule itself
                RecurringRule rule = tx.recurringRule;
                if (command.amount() != null) rule.amount = command.amount();
                if (command.description() != null) rule.description = command.description();
                if (newCategory != null) rule.category = newCategory;
            }
        }
    }

    // ────────────────────────────────────────────────────────────────
    // DELETE OCCURRENCE (scoped)
    // ────────────────────────────────────────────────────────────────

    /**
     * Deletes (soft-delete) a recurring transaction occurrence with the given scope:
     * <ul>
     *   <li>ONLY_THIS: soft-deletes only the specified transaction</li>
     *   <li>THIS_AND_FUTURE: soft-deletes this and all future occurrences, deactivates rule</li>
     *   <li>ALL: soft-deletes all occurrences and deactivates rule</li>
     * </ul>
     */
    @Transactional
    public void deleteOccurrence(UUID transactionId, RecurrenceScope scope) {
        FinancialTransaction tx = findActiveTransaction(transactionId);
        if (tx.recurringRule == null) {
            throw new IllegalStateException("Transaction is not part of a recurring rule");
        }

        LocalDateTime now = LocalDateTime.now();

        switch (scope) {
            case ONLY_THIS -> tx.deletedAt = now;
            case THIS_AND_FUTURE -> {
                List<FinancialTransaction> futures = transactions.list(
                        "recurringRule.id = ?1 and deletedAt is null and recurringInstanceIndex >= ?2",
                        tx.recurringRule.id, tx.recurringInstanceIndex);
                for (FinancialTransaction f : futures) {
                    f.deletedAt = now;
                }
                tx.recurringRule.active = false;
            }
            case ALL -> {
                List<FinancialTransaction> all = transactions.list(
                        "recurringRule.id = ?1 and deletedAt is null",
                        tx.recurringRule.id);
                for (FinancialTransaction a : all) {
                    a.deletedAt = now;
                }
                tx.recurringRule.active = false;
            }
        }
    }

    // ────────────────────────────────────────────────────────────────
    // Helpers
    // ────────────────────────────────────────────────────────────────

    private FinancialTransaction findActiveTransaction(UUID id) {
        FinancialTransaction tx = transactions.findById(id);
        if (tx == null || tx.deletedAt != null) {
            throw new IllegalArgumentException("Transaction not found: " + id);
        }
        return tx;
    }

    private void applyUpdate(FinancialTransaction tx, RecurringUpdateCommand cmd, Category newCategory) {
        if (cmd.amount() != null) tx.amount = cmd.amount();
        if (cmd.description() != null) tx.description = cmd.description();
        if (newCategory != null) tx.category = newCategory;
        if (cmd.paid() != null) tx.paid = cmd.paid();
    }
}
