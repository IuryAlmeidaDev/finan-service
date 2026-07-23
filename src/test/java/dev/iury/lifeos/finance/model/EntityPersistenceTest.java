package dev.iury.lifeos.finance.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.lang.reflect.Field;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import dev.iury.lifeos.finance.common.MutableTimeProvider;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EntityManager;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;

@QuarkusTest
class EntityPersistenceTest {

    @Inject
    EntityManager entityManager;

    @Inject
    MutableTimeProvider timeProvider;

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
    void rejectsAccountInitialBalanceWithSubCentPrecision() {
        Account account = accountWith(new BigDecimal("1.001"));
        assertSubCentPrecisionIsRejected(account);
    }

    @Test
    @TestTransaction
    void rejectsTransactionAmountWithSubCentPrecision() {
        Fixtures fixtures = fixtures(CategoryType.EXPENSE);
        FinancialTransaction transaction = transaction(fixtures);
        transaction.amount = new BigDecimal("1.001");
        assertSubCentPrecisionIsRejected(transaction);
    }

    @Test
    @TestTransaction
    void rejectsInstallmentTotalAmountWithSubCentPrecision() {
        Fixtures fixtures = fixtures(CategoryType.EXPENSE);
        InstallmentGroup group = installmentGroup(fixtures);
        group.totalAmount = new BigDecimal("1.001");
        assertSubCentPrecisionIsRejected(group);
    }

    @Test
    @TestTransaction
    void rejectsRecurringAmountWithSubCentPrecision() {
        Fixtures fixtures = fixtures(CategoryType.EXPENSE);
        RecurringRule rule = recurringRule(fixtures);
        rule.amount = new BigDecimal("1.001");
        assertSubCentPrecisionIsRejected(rule);
    }

    @Test
    @TestTransaction
    void rejectsBudgetLimitWithSubCentPrecision() {
        Fixtures fixtures = fixtures(CategoryType.EXPENSE);
        Budget budget = budget(fixtures);
        budget.limitAmount = new BigDecimal("1.001");
        assertSubCentPrecisionIsRejected(budget);
    }

    @Test
    @TestTransaction
    void rejectsBudgetRolloverWithSubCentPrecision() {
        Fixtures fixtures = fixtures(CategoryType.EXPENSE);
        Budget budget = budget(fixtures);
        budget.rolloverAmount = new BigDecimal("1.001");
        assertSubCentPrecisionIsRejected(budget);
    }

    @Test
    @TestTransaction
    void rejectsIncomeGoalTargetWithSubCentPrecision() {
        Fixtures fixtures = fixtures(CategoryType.INCOME);
        IncomeGoal goal = new IncomeGoal();
        goal.category = fixtures.category();
        goal.month = 9;
        goal.year = 2026;
        goal.targetAmount = new BigDecimal("1.001");
        assertSubCentPrecisionIsRejected(goal);
    }

    @Test
    @TestTransaction
    void usesControlledTimeForCreationAndUpdateCallbacks() {
        Instant createdInstant = Instant.parse("2026-08-01T10:15:30Z");
        timeProvider.set(createdInstant);
        Account account = accountWith(new BigDecimal("10.00"));
        entityManager.persist(account);
        entityManager.flush();

        LocalDateTime created = LocalDateTime.ofInstant(createdInstant, ZoneOffset.UTC);
        assertThat(account.createdAt).isEqualTo(created);
        assertThat(account.updatedAt).isEqualTo(created);

        Instant updatedInstant = Instant.parse("2026-08-02T11:16:31Z");
        timeProvider.set(updatedInstant);
        account.name = "Conta atualizada";
        entityManager.flush();

        assertThat(account.createdAt).isEqualTo(created);
        assertThat(account.updatedAt)
                .isEqualTo(LocalDateTime.ofInstant(updatedInstant, ZoneOffset.UTC));
    }

    @Test
    void mapsAllRelationshipsLazyAndPersistentEnumsAsStrings() throws Exception {
        assertManyToOneIsLazy(Category.class, "parentCategory");
        assertManyToOneIsLazy(InstallmentGroup.class, "account");
        assertManyToOneIsLazy(InstallmentGroup.class, "category");
        assertManyToOneIsLazy(RecurringRule.class, "account");
        assertManyToOneIsLazy(RecurringRule.class, "category");
        assertManyToOneIsLazy(FinancialTransaction.class, "account");
        assertManyToOneIsLazy(FinancialTransaction.class, "destinationAccount");
        assertManyToOneIsLazy(FinancialTransaction.class, "category");
        assertManyToOneIsLazy(FinancialTransaction.class, "recurringRule");
        assertManyToOneIsLazy(FinancialTransaction.class, "installmentGroup");
        assertManyToOneIsLazy(Attachment.class, "transaction");
        assertManyToOneIsLazy(Budget.class, "category");
        assertManyToOneIsLazy(IncomeGoal.class, "category");
        assertManyToOneIsLazy(TransactionTag.class, "transaction");
        assertManyToOneIsLazy(TransactionTag.class, "tag");

        assertEnumIsString(Account.class, "type");
        assertEnumIsString(Category.class, "type");
        assertEnumIsString(InstallmentGroup.class, "status");
        assertEnumIsString(RecurringRule.class, "type");
        assertEnumIsString(RecurringRule.class, "frequency");
        assertEnumIsString(RecurringRule.class, "dayOfWeek");
        assertEnumIsString(FinancialTransaction.class, "type");
        assertEnumIsString(Budget.class, "rolloverType");
    }

    @Test
    @TestTransaction
    void persistsDocumentedDefaults() {
        Fixtures fixtures = fixtures(CategoryType.EXPENSE);
        RecurringRule rule = recurringRule(fixtures);
        Budget budget = budget(fixtures);
        FinancialTransaction transaction = transaction(fixtures);
        entityManager.persist(rule);
        entityManager.persist(budget);
        entityManager.persist(transaction);
        entityManager.flush();
        entityManager.clear();

        Account savedAccount = entityManager.find(Account.class, fixtures.account().id);
        RecurringRule savedRule = entityManager.find(RecurringRule.class, rule.id);
        Budget savedBudget = entityManager.find(Budget.class, budget.id);
        FinancialTransaction savedTransaction =
                entityManager.find(FinancialTransaction.class, transaction.id);

        assertThat(savedAccount.includeInTotal).isTrue();
        assertThat(savedAccount.archived).isFalse();
        assertThat(savedRule.autoConfirm).isFalse();
        assertThat(savedRule.active).isTrue();
        assertThat(savedBudget.rolloverType).isEqualTo(RolloverType.NO_ROLLOVER);
        assertThat(savedBudget.rolloverAmount).isEqualByComparingTo("0.00");
        assertThat(savedBudget.includePending).isFalse();
        assertThat(savedTransaction.paid).isFalse();
        assertThat(savedTransaction.ignoredFromBudget).isFalse();
        assertThat(savedTransaction.ignoredFromReports).isFalse();
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
        Account destinationAccount = new Account();
        destinationAccount.name = "Conta destino";
        destinationAccount.type = AccountType.SAVINGS;
        destinationAccount.initialBalance = new BigDecimal("0.00");
        destinationAccount.initialBalanceDate = LocalDate.of(2026, 7, 1);
        entityManager.persist(destinationAccount);
        transaction.linkedTaskId = UUID.fromString("e03d9826-408f-4821-b67d-b170fe6eb77f");
        transaction.recurringRule = recurringRule;
        transaction.recurringInstanceIndex = 2;
        transaction.installmentGroup = installmentGroup;
        transaction.installmentNumber = 1;
        transaction.totalInstallments = 10;
        transaction.ignoredFromBudget = true;
        transaction.ignoredFromReports = true;
        transaction.deletedAt = LocalDateTime.of(2026, 8, 11, 9, 30);
        entityManager.persist(transaction);

        FinancialTransaction transfer = new FinancialTransaction();
        transfer.account = account;
        transfer.destinationAccount = destinationAccount;
        transfer.amount = new BigDecimal("50.00");
        transfer.type = TransactionType.TRANSFER;
        transfer.date = LocalDate.of(2026, 8, 12);
        entityManager.persist(transfer);

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
        assertThat(savedTransaction.linkedTaskId)
                .isEqualTo(UUID.fromString("e03d9826-408f-4821-b67d-b170fe6eb77f"));
        assertThat(savedTransaction.recurringRule.id).isEqualTo(recurringRule.id);
        assertThat(savedTransaction.recurringInstanceIndex).isEqualTo(2);
        assertThat(savedTransaction.installmentGroup.id).isEqualTo(installmentGroup.id);
        assertThat(savedTransaction.installmentNumber).isEqualTo(1);
        assertThat(savedTransaction.totalInstallments).isEqualTo(10);
        assertThat(savedTransaction.ignoredFromBudget).isTrue();
        assertThat(savedTransaction.ignoredFromReports).isTrue();
        assertThat(savedTransaction.deletedAt)
                .isEqualTo(LocalDateTime.of(2026, 8, 11, 9, 30));
        assertThat(savedTransaction.createdAt).isNotNull();
        assertThat(savedTransaction.updatedAt).isNotNull();
        FinancialTransaction savedTransfer =
                entityManager.find(FinancialTransaction.class, transfer.id);
        assertThat(savedTransfer.destinationAccount.id).isEqualTo(destinationAccount.id);

        RecurringRule savedRule = entityManager.find(RecurringRule.class, recurringRule.id);
        assertThat(savedRule.frequency).isEqualTo(RecurringFrequency.MONTHLY);
        assertThat(savedRule.dayOfMonth).isEqualTo(10);
        assertThat(savedRule.dayOfWeek).isEqualTo(DayOfWeek.FRIDAY);
        assertThat(savedRule.startDate).isEqualTo(LocalDate.of(2026, 7, 10));
        assertThat(savedRule.endDate).isEqualTo(LocalDate.of(2027, 7, 10));
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
        assertThat(savedGroup.firstInstallmentDate).isEqualTo(LocalDate.of(2026, 8, 10));
        assertThat(savedGroup.category.id).isEqualTo(category.id);
        assertThat(savedGroup.status).isEqualTo(InstallmentStatus.ACTIVE);

        TransactionTag savedLink = entityManager.find(
                TransactionTag.class, new TransactionTagId(transaction.id, tag.id));
        assertThat(savedLink.transaction.id).isEqualTo(transaction.id);
        assertThat(savedLink.tag.id).isEqualTo(tag.id);
    }

    private void assertSubCentPrecisionIsRejected(Object entity) {
        assertThatThrownBy(() -> {
            entityManager.persist(entity);
            entityManager.flush();
        }).hasRootCauseInstanceOf(ArithmeticException.class);
    }

    private Fixtures fixtures(CategoryType categoryType) {
        Account account = accountWith(new BigDecimal("100.00"));
        entityManager.persist(account);

        Category category = new Category();
        category.name = "Fixture " + UUID.randomUUID();
        category.type = categoryType;
        entityManager.persist(category);
        return new Fixtures(account, category);
    }

    private Account accountWith(BigDecimal initialBalance) {
        Account account = new Account();
        account.name = "Conta fixture";
        account.type = AccountType.CHECKING;
        account.initialBalance = initialBalance;
        account.initialBalanceDate = LocalDate.of(2026, 7, 1);
        return account;
    }

    private FinancialTransaction transaction(Fixtures fixtures) {
        FinancialTransaction transaction = new FinancialTransaction();
        transaction.account = fixtures.account();
        transaction.amount = new BigDecimal("10.00");
        transaction.type = fixtures.category().type == CategoryType.INCOME
                ? TransactionType.INCOME
                : TransactionType.EXPENSE;
        transaction.date = LocalDate.of(2026, 9, 1);
        transaction.category = fixtures.category();
        return transaction;
    }

    private InstallmentGroup installmentGroup(Fixtures fixtures) {
        InstallmentGroup group = new InstallmentGroup();
        group.account = fixtures.account();
        group.description = "Parcelamento fixture";
        group.totalAmount = new BigDecimal("20.00");
        group.totalInstallments = 2;
        group.firstInstallmentDate = LocalDate.of(2026, 9, 1);
        group.category = fixtures.category();
        group.status = InstallmentStatus.ACTIVE;
        return group;
    }

    private RecurringRule recurringRule(Fixtures fixtures) {
        RecurringRule rule = new RecurringRule();
        rule.account = fixtures.account();
        rule.amount = new BigDecimal("10.00");
        rule.type = fixtures.category().type == CategoryType.INCOME
                ? TransactionType.INCOME
                : TransactionType.EXPENSE;
        rule.category = fixtures.category();
        rule.frequency = RecurringFrequency.MONTHLY;
        rule.startDate = LocalDate.of(2026, 9, 1);
        return rule;
    }

    private Budget budget(Fixtures fixtures) {
        Budget budget = new Budget();
        budget.category = fixtures.category();
        budget.month = 9;
        budget.year = 2026;
        budget.limitAmount = new BigDecimal("100.00");
        return budget;
    }

    private void assertManyToOneIsLazy(Class<?> entityType, String fieldName) throws Exception {
        ManyToOne relation = entityType.getDeclaredField(fieldName).getAnnotation(ManyToOne.class);
        assertThat(relation).as("%s.%s @ManyToOne", entityType.getSimpleName(), fieldName).isNotNull();
        assertThat(relation.fetch()).isEqualTo(FetchType.LAZY);
    }

    private void assertEnumIsString(Class<?> entityType, String fieldName) throws Exception {
        Field field = entityType.getDeclaredField(fieldName);
        Enumerated enumerated = field.getAnnotation(Enumerated.class);
        assertThat(enumerated).as("%s.%s @Enumerated", entityType.getSimpleName(), fieldName).isNotNull();
        assertThat(enumerated.value()).isEqualTo(EnumType.STRING);
    }

    private record Fixtures(Account account, Category category) {
    }
}
