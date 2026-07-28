package dev.iury.lifeos.finance.category;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.iury.lifeos.finance.model.Account;
import dev.iury.lifeos.finance.model.AccountType;
import dev.iury.lifeos.finance.model.Category;
import dev.iury.lifeos.finance.model.CategoryType;
import dev.iury.lifeos.finance.model.TransactionType;
import dev.iury.lifeos.finance.repository.AccountRepository;
import dev.iury.lifeos.finance.repository.CategoryRepository;
import dev.iury.lifeos.finance.repository.TransactionRepository;
import dev.iury.lifeos.finance.transaction.TransactionService;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@QuarkusTest
class CategoryServiceTest {

    @Inject CategoryService categoryService;
    @Inject CategoryRepository categories;
    @Inject AccountRepository accounts;
    @Inject TransactionService transactionService;
    @Inject TransactionRepository transactions;

    private Category incomeCat;
    private Category expenseCat;
    private Category systemCat;
    private Account account;

    @BeforeEach
    @Transactional
    void setup() {
        cleanAll();

        incomeCat = new Category();
        incomeCat.name = "Income";
        incomeCat.type = CategoryType.INCOME;
        categories.persist(incomeCat);

        expenseCat = new Category();
        expenseCat.name = "Expense";
        expenseCat.type = CategoryType.EXPENSE;
        categories.persist(expenseCat);

        systemCat = new Category();
        systemCat.name = "System";
        systemCat.type = CategoryType.EXPENSE;
        systemCat.system = true;
        categories.persist(systemCat);

        account = new Account();
        account.name = "Main";
        account.type = AccountType.CHECKING;
        account.initialBalance = BigDecimal.ZERO;
        account.initialBalanceDate = LocalDate.now();
        accounts.persist(account);
    }

    @AfterEach
    @Transactional
    void teardown() {
        cleanAll();
    }

    private void cleanAll() {
        transactions.deleteAll();
        categories.getEntityManager().createQuery("delete from Budget").executeUpdate();
        categories.getEntityManager().createQuery("delete from IncomeGoal").executeUpdate();
        categories.getEntityManager().createQuery("delete from InstallmentGroup").executeUpdate();
        categories.getEntityManager().createQuery("delete from RecurringRule").executeUpdate();
        accounts.deleteAll();
        categories.delete("name = 'System'");
        categories.delete("parentCategory is not null and system = false");
        categories.delete("system = false");
    }

    @Test
    @Transactional
    void shouldCreateCategory() {
        Category cat = categoryService.create("New Income", CategoryType.INCOME, null, "icon", "#ffffff");
        assertThat(cat.id).isNotNull();
        assertThat(cat.name).isEqualTo("New Income");
        assertThat(cat.type).isEqualTo(CategoryType.INCOME);
    }

    @Test
    @Transactional
    void shouldCreateSubcategoryInheritingType() {
        Category sub = categoryService.create("Sub Expense", CategoryType.INCOME, expenseCat.id, null, null);
        assertThat(sub.type).isEqualTo(CategoryType.EXPENSE); // Inherits from parent despite being passed INCOME
        assertThat(sub.parentCategory.id).isEqualTo(expenseCat.id);
    }

    @Test
    void shouldRejectThirdLevelCategory() {
        Category sub = createCategoryTx("Sub", CategoryType.EXPENSE, expenseCat.id);
        assertThatThrownBy(() -> createCategoryTx("SubSub", CategoryType.EXPENSE, sub.id))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("depth");
    }

    @Test
    void shouldRejectModificationOfSystemCategory() {
        assertThatThrownBy(() -> categoryService.update(systemCat.id, "New Name", null, null, null))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("system");
    }

    @Test
    void shouldRejectDeletionOfSystemCategory() {
        assertThatThrownBy(() -> categoryService.delete(systemCat.id))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("system");
    }

    @Test
    void shouldRejectDeletionOfCategoryWithTransactions() {
        createTxTx(expenseCat.id);
        assertThatThrownBy(() -> categoryService.delete(expenseCat.id))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("transactions");
    }

    @Test
    void shouldRejectDeletionOfCategoryWithSubcategories() {
        createCategoryTx("Sub", CategoryType.EXPENSE, expenseCat.id);
        assertThatThrownBy(() -> categoryService.delete(expenseCat.id))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("subcategories");
    }

    @Test
    @Transactional
    void shouldMigrateCategoryAtomic() {
        Category oldCat = categoryService.create("Old", CategoryType.EXPENSE, null, null, null);
        Category newCat = categoryService.create("New", CategoryType.EXPENSE, null, null, null);
        
        transactionService.create(account.id, TransactionType.EXPENSE, new BigDecimal("10.00"), LocalDate.now(), oldCat.id, "Desc", false, false);
        
        long count = categoryService.migrate(oldCat.id, newCat.id);
        assertThat(count).isEqualTo(1);
        categories.getEntityManager().flush();
        categories.getEntityManager().clear();
        
        assertThat(transactions.findAll().firstResult().category.id).isEqualTo(newCat.id);
        assertThat(categories.findById(oldCat.id).archived).isTrue();
    }

    @Test
    void shouldRejectMigrationToDifferentType() {
        Category oldCat = createCategoryTx("Old", CategoryType.EXPENSE, null);
        assertThatThrownBy(() -> categoryService.migrate(oldCat.id, incomeCat.id))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("type");
    }

    @Transactional
    Category createCategoryTx(String name, CategoryType type, UUID parentId) {
        return categoryService.create(name, type, parentId, null, null);
    }

    @Transactional
    void createTxTx(UUID categoryId) {
        transactionService.create(account.id, TransactionType.EXPENSE, new BigDecimal("10.00"), LocalDate.now(), categoryId, "Desc", false, false);
    }
}
