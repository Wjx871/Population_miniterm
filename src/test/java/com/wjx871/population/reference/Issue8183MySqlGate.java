package com.wjx871.population.reference;

import static org.assertj.core.api.Assertions.assertThat;
import java.sql.*;
import java.util.regex.*;
import org.springframework.test.context.DynamicPropertyRegistry;

final class Issue8183MySqlGate {
    static final Pattern URL = Pattern.compile("^jdbc:mysql://127\\.0\\.0\\.1:(?<port>[0-9]+)/(?<schema>population_miniterm_mysql_gate_issue8183_[a-z0-9_]+)\\?(?<options>.*)$");
    static String required(String key) {
        String v = System.getenv(key);
        if (v == null || v.isBlank()) throw new IllegalStateException("Set " + key + " for the isolated Issue81/83 gate");
        return v;
    }
    static Matcher guard() {
        var m = URL.matcher(required("MYSQL84_GATE_DB_URL"));
        if (!m.matches() || m.group("port").equals("3306")
            || !Pattern.compile("(?:^|&)createDatabaseIfNotExist=false(?:&|$)").matcher(m.group("options")).find()
            || required("MYSQL84_GATE_DB_USERNAME").equalsIgnoreCase("root")
            || required("MYSQL84_GATE_DB_USERNAME").equals(required("MYSQL84_GATE_ADMIN_USERNAME")))
            throw new IllegalStateException("Refusing a non-dedicated Issue81/83 endpoint or app identity");
        return m;
    }
    static void configure(DynamicPropertyRegistry p) {
        guard();
        p.add("spring.sql.init.mode", () -> "never");
        p.add("spring.datasource.url", () -> required("MYSQL84_GATE_DB_URL"));
        p.add("spring.datasource.username", () -> required("MYSQL84_GATE_DB_USERNAME"));
        p.add("spring.datasource.password", () -> required("MYSQL84_GATE_DB_PASSWORD"));
        p.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
    }
    static void verify(Connection c) throws Exception {
        var m = guard();
        try (var s = c.createStatement(); var r = s.executeQuery(
                "SELECT @@version,@@port,@@bind_address,@@transaction_isolation,DATABASE(),CURRENT_USER()")) {
            assertThat(r.next()).isTrue(); assertThat(r.getString(1)).startsWith("8.4.");
            assertThat(r.getInt(2)).isEqualTo(Integer.parseInt(m.group("port")));
            assertThat(r.getString(3)).isEqualTo("127.0.0.1");
            assertThat(r.getString(4)).isEqualTo("REPEATABLE-READ");
            assertThat(r.getString(5)).isEqualTo(m.group("schema"));
            assertThat(r.getString(6)).startsWith(required("MYSQL84_GATE_DB_USERNAME") + "@");
            System.out.println("ISSUE8183 MYSQL identity=" + r.getString(1) + " port=" + r.getInt(2)
                + " isolation=" + r.getString(4) + " schema=" + r.getString(5) + " user=" + r.getString(6));
        }
    }
    static Connection admin() throws Exception {
        guard();
        return DriverManager.getConnection(required("MYSQL84_GATE_DB_URL"),
            required("MYSQL84_GATE_ADMIN_USERNAME"), required("MYSQL84_GATE_ADMIN_PASSWORD"));
    }
}
