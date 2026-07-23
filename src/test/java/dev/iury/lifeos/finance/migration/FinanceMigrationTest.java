package dev.iury.lifeos.finance.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.agroal.api.AgroalDataSource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

@QuarkusTest
class FinanceMigrationTest {

    private static final Set<String> EXPECTED_TABLES = Set.of(
            "account",
            "category",
            "financial_transaction",
            "attachment",
            "installment_group",
            "recurring_rule",
            "budget",
            "income_goal",
            "tag",
            "transaction_tag");

    @Inject
    AgroalDataSource dataSource;

    @Test
    void createsFinanceSchema() throws Exception {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.executeQuery("select count(*) from account");

            Set<String> actualTables = new HashSet<>();
            try (PreparedStatement query = connection.prepareStatement("""
                    select table_name
                      from information_schema.tables
                     where table_schema = 'public'
                       and table_name = any (?)
                    """)) {
                query.setArray(1, connection.createArrayOf("varchar", EXPECTED_TABLES.toArray()));
                try (ResultSet result = query.executeQuery()) {
                    while (result.next()) {
                        actualTables.add(result.getString(1));
                    }
                }
            }

            assertThat(actualTables).containsExactlyInAnyOrderElementsOf(EXPECTED_TABLES);
        }
    }

    @Test
    void seedsSystemCategories() throws Exception {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement query = connection.prepareStatement("""
                        select name
                          from category
                         where is_system = true
                           and name in ('Não Categorizado', 'Ajuste de Saldo')
                        """)) {
            Set<String> categories = new HashSet<>();
            try (ResultSet result = query.executeQuery()) {
                while (result.next()) {
                    categories.add(result.getString(1));
                }
            }

            assertThat(categories).containsExactlyInAnyOrder("Não Categorizado", "Ajuste de Saldo");
        }
    }

    @Test
    void rejectsDuplicateBudgetForCategoryYearAndMonth() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            UUID categoryId = UUID.randomUUID();
            try (PreparedStatement category = connection.prepareStatement("""
                    insert into category (id, name, type)
                    values (?, 'Categoria do orçamento', 'EXPENSE')
                    """)) {
                category.setObject(1, categoryId);
                category.executeUpdate();
            }

            String insertBudget = """
                    insert into budget (id, category_id, month, year, limit_amount)
                    values (?, ?, 7, 2026, 100.00)
                    """;
            try (PreparedStatement first = connection.prepareStatement(insertBudget)) {
                first.setObject(1, UUID.randomUUID());
                first.setObject(2, categoryId);
                first.executeUpdate();
            }

            assertThatThrownBy(() -> {
                try (PreparedStatement duplicate = connection.prepareStatement(insertBudget)) {
                    duplicate.setObject(1, UUID.randomUUID());
                    duplicate.setObject(2, categoryId);
                    duplicate.executeUpdate();
                }
            }).isInstanceOf(SQLException.class)
                    .hasMessageContaining("budget_category_id_year_month_key");
        }
    }
}
