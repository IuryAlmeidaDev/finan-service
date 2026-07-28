package dev.iury.lifeos.finance.transaction;

import java.math.BigDecimal;
import java.util.UUID;

import dev.iury.lifeos.finance.model.Account;
import dev.iury.lifeos.finance.model.Category;
import dev.iury.lifeos.finance.model.CategoryType;
import dev.iury.lifeos.finance.model.TransactionType;
import dev.iury.lifeos.finance.repository.AccountRepository;
import dev.iury.lifeos.finance.repository.CategoryRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class TransactionValidator {

    @Inject
    AccountRepository accounts;

    @Inject
    CategoryRepository categories;

    /**
     * Validates an account is active (not archived, not null).
     */
    public Account requireActiveAccount(UUID accountId) {
        if (accountId == null) {
            throw new IllegalArgumentException("Account id must not be null");
        }
        Account account = accounts.findById(accountId);
        if (account == null) {
            throw new IllegalArgumentException("Account not found: " + accountId);
        }
        if (account.archived) {
            throw new IllegalStateException("Account is archived: " + accountId);
        }
        return account;
    }

    /**
     * Validates that amount is strictly positive.
     */
    public void requirePositiveAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Transaction amount must be positive");
        }
    }

    /**
     * Validates that category type matches the expected transaction type.
     * For INCOME transactions, category must be CategoryType.INCOME.
     * For EXPENSE transactions, category must be CategoryType.EXPENSE.
     * TRANSFER and BALANCE_ADJUSTMENT may have no category.
     */
    public Category requireCompatibleCategory(UUID categoryId, TransactionType type) {
        if (categoryId == null) {
            return null;
        }
        Category category = categories.findById(categoryId);
        if (category == null) {
            throw new IllegalArgumentException("Category not found: " + categoryId);
        }
        if (type == TransactionType.INCOME && category.type != CategoryType.INCOME) {
            throw new IllegalArgumentException(
                    "INCOME transaction requires an INCOME category, got: " + category.type);
        }
        if (type == TransactionType.EXPENSE && category.type != CategoryType.EXPENSE) {
            throw new IllegalArgumentException(
                    "EXPENSE transaction requires an EXPENSE category, got: " + category.type);
        }
        return category;
    }

    /**
     * Validates that destination account is present, active, and different from origin.
     */
    public Account requireValidDestination(UUID originAccountId, UUID destinationAccountId) {
        if (destinationAccountId == null) {
            throw new IllegalArgumentException("Transfer destination account must not be null");
        }
        if (destinationAccountId.equals(originAccountId)) {
            throw new IllegalArgumentException(
                    "Transfer destination account must differ from origin account");
        }
        return requireActiveAccount(destinationAccountId);
    }

    /**
     * Rejects BALANCE_ADJUSTMENT type in generic create path.
     */
    public void rejectBalanceAdjustmentType(TransactionType type) {
        if (type == TransactionType.BALANCE_ADJUSTMENT) {
            throw new IllegalArgumentException(
                    "BALANCE_ADJUSTMENT transactions must be created via the adjust() method");
        }
    }
}
