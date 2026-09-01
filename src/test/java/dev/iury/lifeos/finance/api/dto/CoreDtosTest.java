package dev.iury.lifeos.finance.api.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import dev.iury.lifeos.finance.model.Category;
import dev.iury.lifeos.finance.model.CategoryType;
import dev.iury.lifeos.finance.model.Account;
import dev.iury.lifeos.finance.model.FinancialTransaction;
import dev.iury.lifeos.finance.model.Tag;
import dev.iury.lifeos.finance.model.TransactionType;

class CoreDtosTest {

    @Test
    void mapsCoreEntitiesToApiResponses() {
        Category category = new Category();
        category.id = UUID.randomUUID();
        category.name = "Mercado";
        category.type = CategoryType.EXPENSE;
        Tag tag = new Tag();
        tag.id = UUID.randomUUID();
        tag.name = "Essencial";
        FinancialTransaction transaction = new FinancialTransaction();
        transaction.id = UUID.randomUUID();
        transaction.amount = new BigDecimal("50.00");
        transaction.date = LocalDate.of(2026, 9, 1);
        transaction.type = TransactionType.EXPENSE;
        transaction.category = category;
        Account account = new Account();
        account.id = UUID.randomUUID();
        transaction.account = account;

        assertThat(CategoryDtos.Response.from(category).name()).isEqualTo("Mercado");
        assertThat(TagDtos.Response.from(tag).name()).isEqualTo("Essencial");
        assertThat(TransactionDtos.Response.from(transaction).amount()).isEqualByComparingTo("50.00");
    }
}
