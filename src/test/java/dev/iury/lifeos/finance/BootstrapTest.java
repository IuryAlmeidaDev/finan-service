package dev.iury.lifeos.finance;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import org.eclipse.microprofile.config.Config;
import org.junit.jupiter.api.Test;

import io.agroal.api.AgroalDataSource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

@QuarkusTest
class BootstrapTest {

    @Inject
    Config config;

    @Inject
    AgroalDataSource dataSource;

    @Test
    void applicationStarts() throws Exception {
        assertThat(Runtime.version().feature()).isEqualTo(21);
        assertThat(config.getValue("quarkus.http.port", Integer.class)).isEqualTo(8082);
        assertThat(config.getOptionalValue("quarkus.flyway.migrate-at-start", Boolean.class))
                .contains(true);

        try (Connection connection = dataSource.getConnection()) {
            assertThat(connection.isValid(2)).isTrue();

            try (Statement statement = connection.createStatement();
                    ResultSet result = statement.executeQuery(
                            "select current_database(), current_setting('server_version_num')::int")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString(1)).isEqualTo("finance_db");
                assertThat(result.getInt(2) / 10_000).isEqualTo(16);
            }
        }
    }
}
