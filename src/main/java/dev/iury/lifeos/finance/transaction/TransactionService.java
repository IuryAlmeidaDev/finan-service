package dev.iury.lifeos.finance.transaction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import dev.iury.lifeos.finance.account.Balance;
import dev.iury.lifeos.finance.account.BalanceCalculator;
import dev.iury.lifeos.finance.model.Account;
import dev.iury.lifeos.finance.model.Category;
import dev.iury.lifeos.finance.model.FinancialTransaction;
import dev.iury.lifeos.finance.model.TransactionType;
import dev.iury.lifeos.finance.repository.TransactionRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class TransactionService {

    @Inject
    TransactionRepository transactions;

    @Inject
    TransactionValidator validator;

    @Inject
    BalanceCalculator balanceCalculator;

    // ────────────────────────────────────────────────────────────────
    // FIND
    // ────────────────────────────────────────────────────────────────

    /**
     * Returns the transaction by id. Throws IllegalArgumentException if not found
     * or soft-deleted.
     */
    public FinancialTransaction findById(UUID id) {
        FinancialTransaction tx = transactions.findById(id);
        if (tx == null || tx.deletedAt != null) {
            throw new IllegalArgumentException("Transaction not found: " + id);
        }
        return tx;
    }

    // ────────────────────────────────────────────────────────────────
    // CREATE — generic (INCOME / EXPENSE)
    // ────────────────────────────────────────────────────────────────

    /**
     * Creates a plain INCOME or EXPENSE transaction. TRANSFER must go through
     * {@link #createTransfer}. BALANCE_ADJUSTMENT must go through {@link #adjust}.
     */
    @Transactional
    public FinancialTransaction create(
            UUID accountId,
            TransactionType type,
            BigDecimal amount,
            LocalDate date,
            UUID categoryId,
            String description,
            boolean paid,
            boolean ignoredFromBudget) {

        validator.rejectSpecialTransactionType(type);
        Account account = validator.requireActiveAccount(accountId);
        validator.requirePositiveAmount(amount);
        Category category = validator.requireCompatibleCategory(categoryId, type);

        FinancialTransaction tx = new FinancialTransaction();
        tx.account = account;
        tx.type = type;
        tx.amount = amount;
        tx.date = date;
        tx.category = category;
        tx.description = description;
        tx.paid = paid;
        tx.ignoredFromBudget = ignoredFromBudget;
        transactions.persist(tx);
        return tx;
    }

    // ────────────────────────────────────────────────────────────────
    // CREATE — transfer
    // ────────────────────────────────────────────────────────────────

    @Transactional
    public FinancialTransaction createTransfer(
            UUID accountId,
            UUID destinationAccountId,
            BigDecimal amount,
            LocalDate date,
            String description,
            boolean paid) {

        Account account = validator.requireActiveAccount(accountId);
        Account destination = validator.requireValidDestination(accountId, destinationAccountId);
        validator.requirePositiveAmount(amount);

        FinancialTransaction tx = new FinancialTransaction();
        tx.account = account;
        tx.destinationAccount = destination;
        tx.type = TransactionType.TRANSFER;
        tx.amount = amount;
        tx.date = date;
        tx.description = description;
        tx.paid = paid;
        transactions.persist(tx);
        return tx;
    }

    // ────────────────────────────────────────────────────────────────
    // UPDATE
    // ────────────────────────────────────────────────────────────────

    @Transactional
    public FinancialTransaction update(
            UUID id,
            BigDecimal amount,
            LocalDate date,
            UUID categoryId,
            String description,
            boolean paid,
            boolean ignoredFromBudget) {

        FinancialTransaction tx = findById(id);
        validator.requirePositiveAmount(amount);

        Category category = validator.requireCompatibleCategory(categoryId, tx.type);
        tx.amount = amount;
        tx.date = date;
        tx.category = category;
        tx.description = description;
        tx.paid = paid;
        tx.ignoredFromBudget = ignoredFromBudget;
        return tx;
    }

    // ────────────────────────────────────────────────────────────────
    // PAY / UNPAY
    // ────────────────────────────────────────────────────────────────

    @Transactional
    public void pay(UUID id) {
        FinancialTransaction tx = requireNotDeleted(id);
        tx.paid = true;
    }

    @Transactional
    public void unpay(UUID id) {
        FinancialTransaction tx = requireNotDeleted(id);
        tx.paid = false;
    }

    // ────────────────────────────────────────────────────────────────
    // SOFT DELETE
    // ────────────────────────────────────────────────────────────────

    @Transactional
    public void delete(UUID id) {
        FinancialTransaction tx = transactions.findById(id);
        if (tx == null) {
            throw new IllegalArgumentException("Transaction not found: " + id);
        }
        tx.deletedAt = LocalDateTime.now();
    }

    // ────────────────────────────────────────────────────────────────
    // BALANCE ADJUSTMENT
    // ────────────────────────────────────────────────────────────────

    /**
     * Creates a BALANCE_ADJUSTMENT transaction if the difference between the desired
     * new balance and the current realized balance is non-zero.
     * <ul>
     *   <li>Positive difference (target &gt; current) → incoming adjustment:
     *       {@code destinationAccount = account}, {@code account = account}.</li>
     *   <li>Negative difference (target &lt; current) → outgoing adjustment:
     *       {@code account = account}, {@code destinationAccount = null}.</li>
     *   <li>Zero difference → no transaction is created.</li>
     * </ul>
     */
    @Transactional
    public void adjust(UUID accountId, BigDecimal targetBalance) {
        Account account = validator.requireActiveAccount(accountId);

        Balance current = balanceCalculator.calculate(account, LocalDate.now());
        BigDecimal difference = targetBalance.subtract(current.realized());

        if (difference.compareTo(BigDecimal.ZERO) == 0) {
            return;
        }

        FinancialTransaction adj = new FinancialTransaction();
        adj.type = TransactionType.BALANCE_ADJUSTMENT;
        adj.amount = difference.abs();
        adj.date = LocalDate.now();
        adj.paid = true;

        if (difference.compareTo(BigDecimal.ZERO) > 0) {
            // Incoming: account is destination (increases balance)
            adj.account = account;
            adj.destinationAccount = account;
        } else {
            // Outgoing: account is origin, no destination (decreases balance)
            adj.account = account;
            adj.destinationAccount = null;
        }

        transactions.persist(adj);
    }

    // ────────────────────────────────────────────────────────────────
    // Helpers
    // ────────────────────────────────────────────────────────────────

    private FinancialTransaction requireNotDeleted(UUID id) {
        FinancialTransaction tx = transactions.findById(id);
        if (tx == null) {
            throw new IllegalArgumentException("Transaction not found: " + id);
        }
        if (tx.deletedAt != null) {
            throw new IllegalStateException("Transaction is deleted: " + id);
        }
        return tx;
    }
}
