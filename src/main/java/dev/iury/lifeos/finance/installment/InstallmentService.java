package dev.iury.lifeos.finance.installment;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import dev.iury.lifeos.finance.model.Account;
import dev.iury.lifeos.finance.model.Category;
import dev.iury.lifeos.finance.model.FinancialTransaction;
import dev.iury.lifeos.finance.model.InstallmentGroup;
import dev.iury.lifeos.finance.model.InstallmentStatus;
import dev.iury.lifeos.finance.model.TransactionType;
import dev.iury.lifeos.finance.repository.AccountRepository;
import dev.iury.lifeos.finance.repository.CategoryRepository;
import dev.iury.lifeos.finance.repository.InstallmentGroupRepository;
import dev.iury.lifeos.finance.repository.TransactionRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * Orchestrates installment purchase workflows:
 * <ul>
 *   <li>Create a group + N pending expense transactions</li>
 *   <li>List transactions for a group</li>
 *   <li>Cancel a group (soft-deletes only unpaid installments)</li>
 * </ul>
 */
@ApplicationScoped
public class InstallmentService {

    @Inject
    InstallmentCalculator calculator;

    @Inject
    InstallmentGroupRepository groups;

    @Inject
    TransactionRepository transactions;

    @Inject
    AccountRepository accounts;

    @Inject
    CategoryRepository categories;

    // ────────────────────────────────────────────────────────────────
    // CREATE
    // ────────────────────────────────────────────────────────────────

    /**
     * Creates an installment group with N expense transactions, all initially unpaid.
     *
     * @param accountId   the account to charge
     * @param categoryId  optional category (nullable)
     * @param totalAmount total purchase value
     * @param count       number of installments (≥ 2)
     * @param firstDate   due date of the first installment
     * @param description description for the group and each transaction
     * @return the persisted InstallmentGroup
     */
    @Transactional
    public InstallmentGroup create(
            UUID accountId,
            UUID categoryId,
            BigDecimal totalAmount,
            int count,
            LocalDate firstDate,
            String description) {

        Account account = accounts.findById(accountId);
        if (account == null || account.archived) {
            throw new IllegalArgumentException("Account not found or archived: " + accountId);
        }

        Category category = categoryId != null ? categories.findById(categoryId) : null;
        if (category == null) {
            throw new IllegalArgumentException("Installment expenses require a category");
        }

        List<InstallmentSlice> slices = calculator.split(totalAmount, count, firstDate, description);

        InstallmentGroup group = new InstallmentGroup();
        group.account = account;
        group.description = description;
        group.totalAmount = totalAmount;
        group.totalInstallments = count;
        group.firstInstallmentDate = firstDate;
        group.category = category;
        group.status = InstallmentStatus.ACTIVE;
        groups.persist(group);

        for (InstallmentSlice slice : slices) {
            FinancialTransaction tx = new FinancialTransaction();
            tx.account = account;
            tx.type = TransactionType.EXPENSE;
            tx.amount = slice.amount();
            tx.date = slice.date();
            tx.category = category;
            tx.description = description + " " + slice.label();
            tx.paid = false;
            tx.installmentGroup = group;
            tx.installmentNumber = slice.number();
            tx.totalInstallments = slice.total();
            transactions.persist(tx);
        }

        return group;
    }

    // ────────────────────────────────────────────────────────────────
    // LIST
    // ────────────────────────────────────────────────────────────────

    /**
     * Returns all transactions belonging to the given installment group, ordered by number.
     */
    public List<FinancialTransaction> listByGroup(UUID groupId) {
        return transactions.list(
                "installmentGroup.id = ?1 and deletedAt is null order by installmentNumber", groupId);
    }

    // ────────────────────────────────────────────────────────────────
    // DETAIL
    // ────────────────────────────────────────────────────────────────

    /**
     * Returns the installment group by id.
     *
     * @throws IllegalArgumentException if not found
     */
    public InstallmentGroup findGroup(UUID groupId) {
        InstallmentGroup group = groups.findById(groupId);
        if (group == null) {
            throw new IllegalArgumentException("Installment group not found: " + groupId);
        }
        return group;
    }

    // ────────────────────────────────────────────────────────────────
    // CANCEL
    // ────────────────────────────────────────────────────────────────

    /**
     * Cancels an installment group:
     * <ul>
     *   <li>Marks the group status as CANCELED</li>
     *   <li>Soft-deletes only the <b>unpaid</b> installment transactions</li>
     *   <li>Paid installments are preserved as-is</li>
     * </ul>
     *
     * @throws IllegalArgumentException if group not found
     * @throws IllegalStateException    if group is already canceled
     */
    @Transactional
    public void cancel(UUID groupId) {
        InstallmentGroup group = findGroup(groupId);

        if (group.status == InstallmentStatus.CANCELED) {
            throw new IllegalStateException("Installment group is already canceled: " + groupId);
        }

        group.status = InstallmentStatus.CANCELED;

        LocalDateTime now = LocalDateTime.now();
        List<FinancialTransaction> installments = transactions.list(
                "installmentGroup.id = ?1 and deletedAt is null and paid = false", groupId);
        for (FinancialTransaction tx : installments) {
            tx.deletedAt = now;
        }
    }
}
