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

    private static final Set<String> EXPECTED_SYSTEM_CATEGORIES = Set.of(
            "INCOME|<root>|Salário",
            "INCOME|<root>|Freelance",
            "INCOME|<root>|Rendimentos",
            "INCOME|<root>|Cashback",
            "INCOME|<root>|Presentes",
            "INCOME|<root>|Outros",
            "EXPENSE|<root>|Restaurantes/Delivery",
            "EXPENSE|<root>|Viagens",
            "EXPENSE|<root>|Ajuste de Saldo",
            "EXPENSE|<root>|Não Categorizado",
            "EXPENSE|<root>|Moradia",
            "EXPENSE|<root>|Alimentação",
            "EXPENSE|<root>|Transporte",
            "EXPENSE|<root>|Saúde",
            "EXPENSE|<root>|Educação",
            "EXPENSE|<root>|Lazer",
            "EXPENSE|<root>|Compras",
            "EXPENSE|<root>|Cuidados Pessoais",
            "EXPENSE|<root>|Assinaturas",
            "EXPENSE|Moradia|Aluguel",
            "EXPENSE|Moradia|Condomínio",
            "EXPENSE|Moradia|Luz",
            "EXPENSE|Moradia|Água",
            "EXPENSE|Moradia|Gás",
            "EXPENSE|Moradia|Internet",
            "EXPENSE|Alimentação|Supermercado",
            "EXPENSE|Alimentação|Feira",
            "EXPENSE|Transporte|Combustível",
            "EXPENSE|Transporte|Transporte Público",
            "EXPENSE|Transporte|Uber/99",
            "EXPENSE|Transporte|Estacionamento",
            "EXPENSE|Transporte|Manutenção Veicular",
            "EXPENSE|Transporte|IPVA",
            "EXPENSE|Saúde|Plano de Saúde",
            "EXPENSE|Saúde|Farmácia",
            "EXPENSE|Saúde|Consultas",
            "EXPENSE|Educação|Mensalidade",
            "EXPENSE|Educação|Cursos",
            "EXPENSE|Educação|Material",
            "EXPENSE|Lazer|Cinema",
            "EXPENSE|Lazer|Shows",
            "EXPENSE|Lazer|Jogos",
            "EXPENSE|Compras|Roupas",
            "EXPENSE|Compras|Eletrônicos",
            "EXPENSE|Cuidados Pessoais|Academia",
            "EXPENSE|Cuidados Pessoais|Barbearia/Salão",
            "EXPENSE|Assinaturas|Netflix",
            "EXPENSE|Assinaturas|Spotify");

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
    void seedsAllSystemCategoriesWithExpectedHierarchy() throws Exception {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement query = connection.prepareStatement("""
                        select child.type, parent.name, child.name
                          from category child
                          left join category parent on parent.id = child.parent_category_id
                         where child.is_system = true
                        """)) {
            Set<String> categories = new HashSet<>();
            try (ResultSet result = query.executeQuery()) {
                while (result.next()) {
                    String parent = result.getString(2) == null ? "<root>" : result.getString(2);
                    categories.add(result.getString(1) + "|" + parent + "|" + result.getString(3));
                }
            }

            assertThat(categories).containsExactlyInAnyOrderElementsOf(EXPECTED_SYSTEM_CATEGORIES);
        }
    }

    @Test
    void rejectsInsertingThirdCategoryLevel() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            UUID root = insertCategory(connection, "Raiz insert", "EXPENSE", null);
            UUID child = insertCategory(connection, "Filha insert", "EXPENSE", root);

            assertThatThrownBy(() -> insertCategory(
                    connection, "Neta insert", "EXPENSE", child))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("at most two levels");
        }
    }

    @Test
    void rejectsReparentingCategoryWithChildrenBelowAnotherRoot() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            UUID firstRoot = insertCategory(connection, "Raiz update", "EXPENSE", null);
            insertCategory(connection, "Filha update", "EXPENSE", firstRoot);
            UUID otherRoot = insertCategory(connection, "Outra raiz update", "EXPENSE", null);

            assertThatThrownBy(() -> {
                try (PreparedStatement update = connection.prepareStatement("""
                        update category set parent_category_id = ? where id = ?
                        """)) {
                    update.setObject(1, otherRoot);
                    update.setObject(2, firstRoot);
                    update.executeUpdate();
                }
            }).isInstanceOf(SQLException.class)
                    .hasMessageContaining("at most two levels");
        }
    }

    @Test
    void rejectsChangingParentTypeToMismatchChild() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            UUID parent = insertCategory(connection, "Pai tipo", "EXPENSE", null);
            insertCategory(connection, "Filha tipo", "EXPENSE", parent);

            assertThatThrownBy(() -> updateCategoryType(connection, parent, "INCOME"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("same type");
        }
    }

    @Test
    void rejectsChangingChildTypeToMismatchParent() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            UUID parent = insertCategory(connection, "Pai tipo filha", "EXPENSE", null);
            UUID child = insertCategory(connection, "Filha muda tipo", "EXPENSE", parent);

            assertThatThrownBy(() -> updateCategoryType(connection, child, "INCOME"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("same type");
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

    private UUID insertCategory(
            Connection connection, String name, String type, UUID parentCategoryId) throws SQLException {
        UUID id = UUID.randomUUID();
        try (PreparedStatement insert = connection.prepareStatement("""
                insert into category (id, name, type, parent_category_id)
                values (?, ?, ?, ?)
                """)) {
            insert.setObject(1, id);
            insert.setString(2, name);
            insert.setString(3, type);
            insert.setObject(4, parentCategoryId);
            insert.executeUpdate();
        }
        return id;
    }

    private void updateCategoryType(Connection connection, UUID id, String type) throws SQLException {
        try (PreparedStatement update = connection.prepareStatement("""
                update category set type = ? where id = ?
                """)) {
            update.setString(1, type);
            update.setObject(2, id);
            update.executeUpdate();
        }
    }
}
