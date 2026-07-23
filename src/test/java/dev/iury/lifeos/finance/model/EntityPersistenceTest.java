package dev.iury.lifeos.finance.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

@QuarkusTest
class EntityPersistenceTest {

    @Inject
    EntityManager entityManager;

    @Test
    void exposesTheExactNonPersistentDomainEnumValues() {
        assertThat(Arrays.asList(BalanceEffect.values()))
                .containsExactly(BalanceEffect.INCREASE, BalanceEffect.DECREASE);
        assertThat(Arrays.asList(RecurrenceScope.values()))
                .containsExactly(
                        RecurrenceScope.ONLY_THIS,
                        RecurrenceScope.THIS_AND_FUTURE,
                        RecurrenceScope.ALL);
    }

    @Test
    @TestTransaction
    void persistsAndReloadsTheV1FinanceModel() {
        Account account = new Account();
        account.name = "Conta principal";
        account.type = AccountType.CHECKING;
        account.initialBalance = new BigDecimal("250.75");
        account.initialBalanceDate = LocalDate.of(2026, 7, 1);
        account.color = "#112233";
        account.iconSlug = "wallet";
        account.includeInTotal = false;
        account.archived = true;
        entityManager.persist(account);

        Category category = new Category();
        category.name = "Mercado";
        category.type = CategoryType.EXPENSE;
        category.iconSlug = "cart";
        category.color = "#445566";
        category.system = false;
        category.archived = true;
        category.sortOrder = 7;
        entityManager.persist(category);

        InstallmentGroup installmentGroup = new InstallmentGroup();
        installmentGroup.account = account;
        installmentGroup.description = "Notebook";
        installmentGroup.totalAmount = new BigDecimal("3000.00");
        installmentGroup.totalInstallments = 10;
        installmentGroup.firstInstallmentDate = LocalDate.of(2026, 8, 10);
        installmentGroup.category = category;
        installmentGroup.status = InstallmentStatus.ACTIVE;
        entityManager.persist(installmentGroup);

        RecurringRule recurringRule = new RecurringRule();
        recurringRule.account = account;
        recurringRule.amount = new BigDecimal("99.90");
        recurringRule.type = TransactionType.EXPENSE;
        recurringRule.category = category;
        recurringRule.description = "Internet";
        recurringRule.frequency = RecurringFrequency.MONTHLY;
        recurringRule.dayOfMonth = 10;
        recurringRule.dayOfWeek = DayOfWeek.FRIDAY;
        recurringRule.startDate = LocalDate.of(2026, 7, 10);
        recurringRule.endDate = LocalDate.of(2027, 7, 10);
        recurringRule.autoConfirm = true;
        recurringRule.active = false;
        recurringRule.lastGeneratedDate = LocalDate.of(2026, 6, 10);
        entityManager.persist(recurringRule);

        FinancialTransaction transaction = new FinancialTransaction();
        transaction.account = account;
        transaction.amount = new BigDecimal("300.00");
        transaction.type = TransactionType.EXPENSE;
        transaction.date = LocalDate.of(2026, 8, 10);
        transaction.category = category;
        transaction.description = "Notebook 1/10";
        transaction.notes = "Compra online";
        transaction.paid = true;
        transaction.recurringRule = recurringRule;
        transaction.recurringInstanceIndex = 2;
        transaction.installmentGroup = installmentGroup;
        transaction.installmentNumber = 1;
        transaction.totalInstallments = 10;
        transaction.ignoredFromBudget = true;
        transaction.ignoredFromReports = true;
        entityManager.persist(transaction);

        Budget budget = new Budget();
        budget.category = category;
        budget.month = 8;
        budget.year = 2026;
        budget.limitAmount = new BigDecimal("1000.00");
        budget.rolloverType = RolloverType.POSITIVE_ONLY;
        budget.rolloverAmount = new BigDecimal("25.50");
        budget.includePending = true;
        entityManager.persist(budget);

        IncomeGoal incomeGoal = new IncomeGoal();
        incomeGoal.category = category;
        incomeGoal.month = 8;
        incomeGoal.year = 2026;
        incomeGoal.targetAmount = new BigDecimal("5000.00");
        entityManager.persist(incomeGoal);

        Tag tag = new Tag();
        tag.name = "planejamento";
        tag.color = "#778899";
        entityManager.persist(tag);

        Attachment attachment = new Attachment();
        attachment.transaction = transaction;
        attachment.fileName = "comprovante.pdf";
        attachment.fileType = "application/pdf";
        attachment.fileSize = 2048;
        attachment.storagePath = "2026/07/comprovante.pdf";
        entityManager.persist(attachment);

        TransactionTag transactionTag = new TransactionTag(transaction, tag);
        entityManager.persist(transactionTag);

        entityManager.flush();
        entityManager.clear();

        Account savedAccount = entityManager.find(Account.class, account.id);
        assertThat(savedAccount.name).isEqualTo("Conta principal");
        assertThat(savedAccount.type).isEqualTo(AccountType.CHECKING);
        assertThat(savedAccount.initialBalance).isEqualByComparingTo("250.75");
        assertThat(savedAccount.initialBalanceDate).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(savedAccount.color).isEqualTo("#112233");
        assertThat(savedAccount.iconSlug).isEqualTo("wallet");
        assertThat(savedAccount.includeInTotal).isFalse();
        assertThat(savedAccount.archived).isTrue();
        assertThat(savedAccount.createdAt).isNotNull();
        assertThat(savedAccount.updatedAt).isNotNull();

        Category savedCategory = entityManager.find(Category.class, category.id);
        assertThat(savedCategory.name).isEqualTo("Mercado");
        assertThat(savedCategory.type).isEqualTo(CategoryType.EXPENSE);
        assertThat(savedCategory.sortOrder).isEqualTo(7);
        assertThat(savedCategory.createdAt).isNotNull();

        FinancialTransaction savedTransaction =
                entityManager.find(FinancialTransaction.class, transaction.id);
        assertThat(savedTransaction.account.id).isEqualTo(account.id);
        assertThat(savedTransaction.amount).isEqualByComparingTo("300.00");
        assertThat(savedTransaction.type).isEqualTo(TransactionType.EXPENSE);
        assertThat(savedTransaction.date).isEqualTo(LocalDate.of(2026, 8, 10));
        assertThat(savedTransaction.category.id).isEqualTo(category.id);
        assertThat(savedTransaction.description).isEqualTo("Notebook 1/10");
        assertThat(savedTransaction.notes).isEqualTo("Compra online");
        assertThat(savedTransaction.paid).isTrue();
        assertThat(savedTransaction.recurringRule.id).isEqualTo(recurringRule.id);
        assertThat(savedTransaction.installmentGroup.id).isEqualTo(installmentGroup.id);
        assertThat(savedTransaction.installmentNumber).isEqualTo(1);
        assertThat(savedTransaction.totalInstallments).isEqualTo(10);
        assertThat(savedTransaction.ignoredFromBudget).isTrue();
        assertThat(savedTransaction.ignoredFromReports).isTrue();
        assertThat(savedTransaction.createdAt).isNotNull();
        assertThat(savedTransaction.updatedAt).isNotNull();

        RecurringRule savedRule = entityManager.find(RecurringRule.class, recurringRule.id);
        assertThat(savedRule.frequency).isEqualTo(RecurringFrequency.MONTHLY);
        assertThat(savedRule.dayOfMonth).isEqualTo(10);
        assertThat(savedRule.dayOfWeek).isEqualTo(DayOfWeek.FRIDAY);
        assertThat(savedRule.autoConfirm).isTrue();
        assertThat(savedRule.active).isFalse();
        assertThat(savedRule.lastGeneratedDate).isEqualTo(LocalDate.of(2026, 6, 10));

        Budget savedBudget = entityManager.find(Budget.class, budget.id);
        assertThat(savedBudget.limitAmount).isEqualByComparingTo("1000.00");
        assertThat(savedBudget.rolloverType).isEqualTo(RolloverType.POSITIVE_ONLY);
        assertThat(savedBudget.rolloverAmount).isEqualByComparingTo("25.50");
        assertThat(savedBudget.includePending).isTrue();

        IncomeGoal savedGoal = entityManager.find(IncomeGoal.class, incomeGoal.id);
        assertThat(savedGoal.targetAmount).isEqualByComparingTo("5000.00");

        Tag savedTag = entityManager.find(Tag.class, tag.id);
        assertThat(savedTag.name).isEqualTo("planejamento");
        assertThat(savedTag.color).isEqualTo("#778899");

        Attachment savedAttachment = entityManager.find(Attachment.class, attachment.id);
        assertThat(savedAttachment.transaction.id).isEqualTo(transaction.id);
        assertThat(savedAttachment.fileName).isEqualTo("comprovante.pdf");
        assertThat(savedAttachment.fileType).isEqualTo("application/pdf");
        assertThat(savedAttachment.fileSize).isEqualTo(2048);
        assertThat(savedAttachment.storagePath).isEqualTo("2026/07/comprovante.pdf");

        InstallmentGroup savedGroup =
                entityManager.find(InstallmentGroup.class, installmentGroup.id);
        assertThat(savedGroup.account.id).isEqualTo(account.id);
        assertThat(savedGroup.description).isEqualTo("Notebook");
        assertThat(savedGroup.totalAmount).isEqualByComparingTo("3000.00");
        assertThat(savedGroup.totalInstallments).isEqualTo(10);
        assertThat(savedGroup.status).isEqualTo(InstallmentStatus.ACTIVE);

        TransactionTag savedLink = entityManager.find(
                TransactionTag.class, new TransactionTagId(transaction.id, tag.id));
        assertThat(savedLink.transaction.id).isEqualTo(transaction.id);
        assertThat(savedLink.tag.id).isEqualTo(tag.id);
    }
}
