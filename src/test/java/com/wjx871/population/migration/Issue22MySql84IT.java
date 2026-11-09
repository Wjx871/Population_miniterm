package com.wjx871.population.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Explicit opt-in MySQL 8.4 gate for Issue #22. The {@code *IT} suffix is never selected by the
 * default Maven Surefire patterns, so the normal H2 build never connects to MySQL.
 *
 * <p>Required environment variables:</p>
 * <ul>
 *   <li>{@code MYSQL84_GATE_DB_URL} - dedicated loopback schema, non-3306 port, with
 *       {@code createDatabaseIfNotExist=false}; must already contain the V4_018 baseline plus
 *       V4_019 (or the V4_019-equivalent fresh baseline) and the standard seed users.</li>
 *   <li>{@code MYSQL84_GATE_DB_USERNAME} / {@code MYSQL84_GATE_DB_PASSWORD} - non-root app user.</li>
 *   <li>{@code MYSQL84_GATE_ADMIN_USERNAME} / {@code MYSQL84_GATE_ADMIN_PASSWORD} - separate
 *       administrator account used only by the dedicated gate bootstrap script.</li>
 * </ul>
 * All business scenarios are inherited from {@link Issue22ArchiveAffectedRowsIntegrationTest}, so
 * H2 and MySQL execute the exact same HTTP/JWT/Service/Mapper test paths.
 */
class Issue22MySql84IT extends Issue22ArchiveAffectedRowsIntegrationTest {

    private static final Pattern URL = Pattern.compile(
            "^jdbc:mysql://127\\.0\\.0\\.1:(?<port>[0-9]+)/"
                    + "(?<schema>population_miniterm_mysql_gate_issue22_[a-z0-9_]+)\\?(?<options>.*)$");

    static String required(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Set " + key + " for the isolated Issue22 MySQL gate");
        }
        return value;
    }

    static Matcher guard() {
        Matcher match = URL.matcher(required("MYSQL84_GATE_DB_URL"));
        if (!match.matches() || "3306".equals(match.group("port"))
                || !Pattern.compile("(?:^|&)createDatabaseIfNotExist=false(?:&|$)")
                        .matcher(match.group("options")).find()
                || required("MYSQL84_GATE_DB_USERNAME").equalsIgnoreCase("root")
                || required("MYSQL84_GATE_DB_USERNAME").equals(required("MYSQL84_GATE_ADMIN_USERNAME"))) {
            throw new IllegalStateException("Issue22 gate requires a dedicated loopback schema, explicit non-3306 port and non-root app user");
        }
        return match;
    }

    @DynamicPropertySource
    static void mysql(DynamicPropertyRegistry properties) {
        guard();
        properties.add("spring.sql.init.mode", () -> "never");
        properties.add("spring.datasource.url", () -> required("MYSQL84_GATE_DB_URL"));
        properties.add("spring.datasource.username", () -> required("MYSQL84_GATE_DB_USERNAME"));
        properties.add("spring.datasource.password", () -> required("MYSQL84_GATE_DB_PASSWORD"));
        properties.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        properties.add("spring.datasource.hikari.maximum-pool-size", () -> "12");
    }

    @BeforeEach
    void verifyMysqlIdentity() throws Exception {
        Matcher match = guard();
        try (Connection connection = DriverManager.getConnection(required("MYSQL84_GATE_DB_URL"),
                required("MYSQL84_GATE_DB_USERNAME"), required("MYSQL84_GATE_DB_PASSWORD"));
                Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery(
                        "SELECT @@version,@@port,@@bind_address,@@transaction_isolation,DATABASE(),CURRENT_USER()")) {
            assertThat(result.next()).isTrue();
            assertThat(result.getString(1)).startsWith("8.4.");
            assertThat(result.getInt(2)).isEqualTo(Integer.parseInt(match.group("port")));
            assertThat(result.getString(3)).isEqualTo("127.0.0.1");
            assertThat(result.getString(4)).isEqualTo("REPEATABLE-READ");
            assertThat(result.getString(5)).isEqualTo(match.group("schema"));
            assertThat(result.getString(6)).startsWith(required("MYSQL84_GATE_DB_USERNAME") + "@");
            System.out.println("ISSUE22 MYSQL identity version=" + result.getString(1) + " port="
                    + result.getInt(2) + " isolation=" + result.getString(4) + " schema=" + result.getString(5)
                    + " user=" + result.getString(6));
        }
    }
}
