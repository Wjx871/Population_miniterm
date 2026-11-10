package com.wjx871.population.person.idcard;

import static org.assertj.core.api.Assertions.assertThat;
import java.sql.*;
import java.nio.file.Path;
import java.util.regex.*;
import org.springframework.test.context.DynamicPropertyRegistry;

/** Fail closed before any test can reach a non-owned database. No credentials are printed. */
final class Issue3MySqlSupport {
    static String required(String key) {
        String value = System.getenv(key);
        if(value==null || value.isBlank()) throw new IllegalStateException("Missing isolated gate setting: " + key);
        return value;
    }
    static void configure(DynamicPropertyRegistry registry) {
        Matcher match = Pattern.compile("^jdbc:mysql://127\\.0\\.0\\.1:([0-9]+)/(population_miniterm_mysql_gate_issue3_[a-z0-9_]+)\\?(.*)$")
                .matcher(required("MYSQL84_GATE_DB_URL"));
        if(!match.matches() || match.group(1).equals("3306") || !match.group(3).contains("createDatabaseIfNotExist=false")
                || required("MYSQL84_GATE_DB_USERNAME").equalsIgnoreCase("root")
                || required("MYSQL84_GATE_DB_USERNAME").equals(required("MYSQL84_GATE_ADMIN_USERNAME"))) {
            throw new IllegalStateException("Refusing database outside isolated Issue3 gate");
        }
        try(Connection c=DriverManager.getConnection(required("MYSQL84_GATE_DB_URL"),required("MYSQL84_GATE_DB_USERNAME"),required("MYSQL84_GATE_DB_PASSWORD"));
                Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT @@version,@@port,@@bind_address,@@transaction_isolation,DATABASE(),CURRENT_USER(),@@datadir")) {
            r.next(); assertThat(r.getString(1)).startsWith("8.4."); assertThat(r.getInt(2)).isEqualTo(Integer.parseInt(match.group(1)));
            assertThat(r.getString(3)).isEqualTo("127.0.0.1"); assertThat(r.getString(4)).isEqualTo("REPEATABLE-READ");
            assertThat(r.getString(5)).isEqualTo(match.group(2)); assertThat(r.getString(6)).startsWith(required("MYSQL84_GATE_DB_USERNAME")+"@");
            assertThat(Path.of(r.getString(7)).toAbsolutePath().normalize()).isEqualTo(Path.of(required("MYSQL84_GATE_EXPECTED_DATADIR")).toAbsolutePath().normalize());
            System.out.printf("ISSUE3_MYSQL_IDENTITY version=%s port=%d bind=%s isolation=%s schema=%s account=%s datadirVerified=true%n",r.getString(1),r.getInt(2),r.getString(3),r.getString(4),r.getString(5),r.getString(6));
        } catch(SQLException failure) { throw new IllegalStateException("Cannot verify private MySQL identity",failure); }
        registry.add("spring.sql.init.mode",()->"never");
        registry.add("spring.datasource.url",()->required("MYSQL84_GATE_DB_URL"));
        registry.add("spring.datasource.username",()->required("MYSQL84_GATE_DB_USERNAME"));
        registry.add("spring.datasource.password",()->required("MYSQL84_GATE_DB_PASSWORD"));
        registry.add("spring.datasource.driver-class-name",()->"com.mysql.cj.jdbc.Driver");
        registry.add("spring.datasource.hikari.transaction-isolation",()->"TRANSACTION_REPEATABLE_READ");
    }
    private Issue3MySqlSupport() {}
}