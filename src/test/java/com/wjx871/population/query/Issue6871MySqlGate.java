package com.wjx871.population.query;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.test.context.DynamicPropertyRegistry;

/** Explicit guard for the isolated MySQL 8.4 gate used by issue #68/#71 tests. */
final class Issue6871MySqlGate {
    private static final Pattern URL = Pattern.compile(
            "^jdbc:mysql://127\\.0\\.0\\.1:(?<port>[0-9]+)/"
                    + "(?<schema>population_miniterm_mysql_gate_issue6871_[a-z0-9_]+)\\?(?<options>.*)$");

    private Issue6871MySqlGate() {
    }

    static String required(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Set " + key + " for the isolated issue68/71 MySQL gate");
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
            throw new IllegalStateException("Refusing a non-dedicated issue68/71 MySQL endpoint");
        }
        return match;
    }

    static void configure(DynamicPropertyRegistry properties) {
        guard();
        properties.add("spring.sql.init.mode", () -> "never");
        properties.add("spring.datasource.url", () -> required("MYSQL84_GATE_DB_URL"));
        properties.add("spring.datasource.username", () -> required("MYSQL84_GATE_DB_USERNAME"));
        properties.add("spring.datasource.password", () -> required("MYSQL84_GATE_DB_PASSWORD"));
        properties.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
    }

    static void verify(Connection connection) throws Exception {
        Matcher match = guard();
        try (Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery(
                        "SELECT @@version,@@port,@@bind_address,@@transaction_isolation,DATABASE(),CURRENT_USER()")) {
            assertThat(result.next()).isTrue();
            assertThat(result.getString(1)).startsWith("8.4.");
            assertThat(result.getInt(2)).isEqualTo(Integer.parseInt(match.group("port")));
            assertThat(result.getString(3)).isEqualTo("127.0.0.1");
            assertThat(result.getString(4)).isEqualTo("REPEATABLE-READ");
            assertThat(result.getString(5)).isEqualTo(match.group("schema"));
            assertThat(result.getString(6)).startsWith(required("MYSQL84_GATE_DB_USERNAME") + "@");
            System.out.println("ISSUE6871 MYSQL identity=" + result.getString(1) + " port=" + result.getInt(2)
                    + " isolation=" + result.getString(4) + " schema=" + result.getString(5)
                    + " user=" + result.getString(6));
        }
    }

    static Connection admin() throws Exception {
        guard();
        return DriverManager.getConnection(required("MYSQL84_GATE_DB_URL"),
                required("MYSQL84_GATE_ADMIN_USERNAME"), required("MYSQL84_GATE_ADMIN_PASSWORD"));
    }
}
