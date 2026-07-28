package dev.iury.lifeos.finance.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;`r`nimport org.junit.jupiter.api.Test;

import dev.iury.lifeos.finance.model.Account;
import dev.iury.lifeos.finance.model.AccountType;
import dev.iury.lifeos.finance.model.Category;
import dev.iury.lifeos.finance.model.CategoryType;
import dev.iury.lifeos.finance.model.FinancialTransaction;
import dev.iury.lifeos.finance.model.InstallmentGroup;
import dev.iury.lifeos.finance.model.InstallmentStatus;
import dev.iury.lifeos.finance.model.RecurringFrequency;
import dev.iury.lifeos.finance.model.RecurringRule;
import dev.iury.lifeos.finance.model.Tag;
import dev.iury.lifeos.finance.model.TransactionTag;
import dev.iury.lifeos.finance.model.TransactionType;
import dev.iury.lifeos.finance.transaction.TransactionFilter;
import io.quarkus.panache.common.Page;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;`r`nimport jakarta.transaction.Transactional;

@QuarkusTest
class FinanceRepositoryTest {

    @Inject EntityManager entityManager;
    @Inject TransactionRepository transactions;
    @Inject CategoryRepository categories;`r`n`r`n    @BeforeEach`r`n    @Transactional`r`n    void cleanTestData() {`r`n        transactions.deleteAll();`r`n        entityManager.createQuery("delete from InstallmentGroup").executeUpdate();`r`n        entityManager.createQuery("delete from RecurringRule").executeUpdate();`r`n        entityManager.createQuery("delete from Account").executeUpdate();`r`n        categories.delete("parentCategory is not null and system = false");`r`n        categories.delete("system", false);`r`n    }

    @Test
    @TestTransaction
    void searchesCombinedFiltersWithDescendantsOrderingAndPagination() {
        Dataset d = dataset();

        TransactionFilter filter = new TransactionFilter();
        filter.accountId = d.otherAccount.id;
        filter.accountIds = List.of(d.account.id, d.account.id);
        filter.categoryId = d.parent.id;
        filter.includeCategoryDescendants = true;
        filter.paid = true;
        filter.startDate = LocalDate.of(2026, 7, 1);
        filter.endDate = LocalDate.of(2026, 7, 31);
        filter.minAmount = new BigDecimal("10.00");
        filter.maxAmount = new BigDecimal("30.00");
        filter.search = "merc";
        filter.tagId = d.tag.id;
        filter.installmentGroupId = d.installment.id;
        filter.recurringRuleId = d.recurring.id;
        filter.sortBy = "amount";
        filter.sortDirection = "desc";

        List<FinancialTransaction> result = transactions.search(filter, Page.of(0, 1)).list();

        assertThat(result).extracting(t -> t.description).containsExactly("Mercado especial");
        assertThat(transactions.search(filter, Page.of(1, 1)).list()).isEmpty();
        assertThat(filter.normalizedAccountIds())
                .containsExactly(d.otherAccount.id, d.account.id);
        assertThat(categories.descendantIds(d.parent.id))
                .containsExactlyInAnyOrder(d.parent.id, d.child.id);
        filter.sortBy = "account";
        assertThatThrownBy(() -> transactions.search(filter, Page.ofSize(10)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @TestTransaction
    void aggregateQueriesExcludeDeletedAndPurposeSpecificIgnoredTransactions() {
        Dataset d = dataset();
        LocalDate start = LocalDate.of(2026, 7, 1);
        LocalDate end = LocalDate.of(2026, 7, 31);

        assertThat(transactions.sumPaidForAccount(d.account.id, end))
                .isEqualByComparingTo("20.00");
        assertThat(transactions.sumBudgetExpenses(d.parent.id, true, start, end))
                .isEqualByComparingTo("33.00");
        assertThat(transactions.sumIncomeGoal(d.incomeCategory.id, start, end))
                .isEqualByComparingTo("50.00");
        assertThat(transactions.sumReport(TransactionType.EXPENSE, start, end))
                .isEqualByComparingTo("27.00");
        assertThat(transactions.findByLinkedTaskId(d.linkedTaskId))
                .get().extracting(t -> t.description).isEqualTo("Mercado especial");
    }

    private Dataset dataset() {
        Account account = account("Principal");
        Account other = account("Outra");
        Category parent = category("Casa", CategoryType.EXPENSE, null);
        Category child = category("Mercado", CategoryType.EXPENSE, parent);
        Category income = category("Salário", CategoryType.INCOME, null);
        Tag tag = new Tag();
        tag.name = "essencial";
        entityManager.persist(tag);

        InstallmentGroup installment = new InstallmentGroup();
        installment.account = account;
        installment.description = "Compras";
        installment.totalAmount = new BigDecimal("60.00");
        installment.totalInstallments = 3;
        installment.firstInstallmentDate = LocalDate.of(2026, 7, 5);
        installment.category = child;
        installment.status = InstallmentStatus.ACTIVE;
        entityManager.persist(installment);

        RecurringRule recurring = new RecurringRule();
        recurring.account = account;
        recurring.amount = new BigDecimal("20.00");
        recurring.type = TransactionType.EXPENSE;
        recurring.category = child;
        recurring.frequency = RecurringFrequency.MONTHLY;
        recurring.startDate = LocalDate.of(2026, 7, 1);
        entityManager.persist(recurring);

        UUID linkedTaskId = UUID.randomUUID();
        FinancialTransaction matching = transaction(account, child, "Mercado especial", "20.00",
                TransactionType.EXPENSE, true);
        matching.installmentGroup = installment;
        matching.installmentNumber = 1;
        matching.totalInstallments = 3;
        matching.recurringRule = recurring;
        matching.recurringInstanceIndex = 1;
        matching.linkedTaskId = linkedTaskId;
        entityManager.persist(new TransactionTag(matching, tag));

        transaction(account, child, "Mercado pendente", "10.00",
                TransactionType.EXPENSE, false);
        FinancialTransaction ignoredBudget = transaction(account, child, "Mercado ignorado", "7.00",
                TransactionType.EXPENSE, true);
        ignoredBudget.ignoredFromBudget = true;
        FinancialTransaction ignoredReport = transaction(account, child, "Mercado relatório", "3.00",
                TransactionType.EXPENSE, true);
        ignoredReport.ignoredFromReports = true;
        FinancialTransaction deleted = transaction(account, child, "Mercado deletado", "100.00",
                TransactionType.EXPENSE, true);
        deleted.deletedAt = LocalDateTime.of(2026, 7, 20, 12, 0);
        transaction(account, income, "Salário", "50.00", TransactionType.INCOME, true);
        entityManager.flush();
        return new Dataset(account, other, parent, child, income, tag, installment, recurring, linkedTaskId);
    }

    private Account account(String name) {
        Account account = new Account();
        account.name = name;
        account.type = AccountType.CHECKING;
        account.initialBalance = BigDecimal.ZERO;
        account.initialBalanceDate = LocalDate.of(2026, 1, 1);
        entityManager.persist(account);
        return account;
    }

    private Category category(String name, CategoryType type, Category parent) {
        Category category = new Category();
        category.name = name;
        category.type = type;
        category.parentCategory = parent;
        entityManager.persist(category);
        return category;
    }

    private FinancialTransaction transaction(Account account, Category category, String description,
            String amount, TransactionType type, boolean paid) {
        FinancialTransaction transaction = new FinancialTransaction();
        transaction.account = account;
        transaction.category = category;
        transaction.description = description;
        transaction.amount = new BigDecimal(amount);
        transaction.type = type;
        transaction.paid = paid;
        transaction.date = LocalDate.of(2026, 7, 10);
        entityManager.persist(transaction);
        return transaction;
    }

    private record Dataset(Account account, Account otherAccount, Category parent, Category child,
            Category incomeCategory, Tag tag, InstallmentGroup installment,
            RecurringRule recurring, UUID linkedTaskId) {}
}
