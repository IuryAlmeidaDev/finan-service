package dev.iury.lifeos.finance.tag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.iury.lifeos.finance.model.Account;
import dev.iury.lifeos.finance.model.AccountType;
import dev.iury.lifeos.finance.model.Category;
import dev.iury.lifeos.finance.model.CategoryType;
import dev.iury.lifeos.finance.model.FinancialTransaction;
import dev.iury.lifeos.finance.model.Tag;
import dev.iury.lifeos.finance.model.TransactionType;
import dev.iury.lifeos.finance.repository.AccountRepository;
import dev.iury.lifeos.finance.repository.CategoryRepository;
import dev.iury.lifeos.finance.repository.TagRepository;
import dev.iury.lifeos.finance.repository.TransactionRepository;
import dev.iury.lifeos.finance.transaction.TransactionService;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

@QuarkusTest
class TagServiceTest {

    @Inject TagService tagService;
    @Inject TagRepository tags;
    @Inject TransactionService transactionService;
    @Inject TransactionRepository transactions;
    @Inject AccountRepository accounts;
    @Inject CategoryRepository categories;
    @Inject EntityManager em;

    private Account account;
    private Category expenseCat;
    private FinancialTransaction tx;

    @BeforeEach
    @Transactional
    void setup() {
        cleanAll();

        account = new Account();
        account.name = "Main";
        account.type = AccountType.CHECKING;
        account.initialBalance = BigDecimal.ZERO;
        account.initialBalanceDate = LocalDate.now();
        accounts.persist(account);

        expenseCat = new Category();
        expenseCat.name = "Expense";
        expenseCat.type = CategoryType.EXPENSE;
        categories.persist(expenseCat);

        tx = transactionService.create(account.id, TransactionType.EXPENSE, new BigDecimal("10.00"), LocalDate.now(), expenseCat.id, "Tx", false, false);
    }

    @AfterEach
    @Transactional
    void teardown() {
        cleanAll();
    }

    private void cleanAll() {
        em.createQuery("delete from TransactionTag").executeUpdate();
        tags.deleteAll();
        transactions.deleteAll();
        em.createQuery("delete from Budget").executeUpdate();
        em.createQuery("delete from IncomeGoal").executeUpdate();
        em.createQuery("delete from InstallmentGroup").executeUpdate();
        em.createQuery("delete from RecurringRule").executeUpdate();
        accounts.deleteAll();
        categories.delete("system = false");
    }

    @Test
    @Transactional
    void shouldCreateTag() {
        Tag tag = tagService.create("Groceries", "#ffffff");
        assertThat(tag.id).isNotNull();
        assertThat(tag.name).isEqualTo("Groceries");
    }

    @Test
    void shouldRejectDuplicateTagName() {
        createTagTx("Food");
        assertThatThrownBy(() -> tagService.create("Food", null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("name");
    }

    @Test
    @Transactional
    void shouldAssociateTagsToTransaction() {
        Tag t1 = tagService.create("T1", null);
        Tag t2 = tagService.create("T2", null);

        tagService.setTransactionTags(tx.id, List.of(t1.id, t2.id));
        
        List<Tag> txTags = tagService.getTransactionTags(tx.id);
        assertThat(txTags).hasSize(2).extracting("name").containsExactlyInAnyOrder("T1", "T2");
    }

    @Test
    void shouldRejectMoreThan15Tags() {
        List<java.util.UUID> tagIds = new java.util.ArrayList<>();
        for (int i = 0; i < 16; i++) {
            tagIds.add(createTagTx("Tag" + i).id);
        }

        assertThatThrownBy(() -> tagService.setTransactionTags(tx.id, tagIds))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("15");
    }

    @Test
    @Transactional
    void shouldDeleteTagAndRemoveLinksOnly() {
        Tag tag = tagService.create("ToDelete", null);
        tagService.setTransactionTags(tx.id, List.of(tag.id));
        
        em.flush();
        em.clear();
        
        tagService.delete(tag.id);
        
        em.flush();
        em.clear();
        
        assertThat(tags.findById(tag.id)).isNull();
        assertThat(transactions.findById(tx.id)).isNotNull();
    }

    @Transactional
    Tag createTagTx(String name) {
        return tagService.create(name, null);
    }
}
