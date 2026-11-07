package com.wjx871.population.floating;

import static org.assertj.core.api.Assertions.assertThat;
import java.sql.*;
import java.util.*;
import java.util.concurrent.Future;
import java.util.regex.*;
import org.junit.jupiter.api.AfterEach;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Explicit opt-in gate. Never selected by the default Maven test patterns. */
class Issue34MySql84IT extends Issue34FirstIssueExclusivityTest {
    static final Pattern URL=Pattern.compile("^jdbc:mysql://127\\.0\\.0\\.1:(?<port>[0-9]+)/(?<schema>population_miniterm_mysql_gate_issue34_[a-z0-9_]+)\\?(?<options>.*)$");
    long deadlocksBefore;
    static String required(String key) {
        String value=System.getenv(key);
        if(value==null || value.isBlank())throw new IllegalStateException("Set "+key+" for the isolated Issue34 MySQL gate");
        return value;
    }
    static void guard() {
        Matcher match=URL.matcher(required("MYSQL84_GATE_DB_URL"));
        if(!match.matches() || match.group("port").equals("3306")
            || !Pattern.compile("(?:^|&)createDatabaseIfNotExist=false(?:&|$)").matcher(match.group("options")).find()
            || required("MYSQL84_GATE_DB_USERNAME").equalsIgnoreCase("root")
            || required("MYSQL84_GATE_DB_USERNAME").equals(required("MYSQL84_GATE_ADMIN_USERNAME")))
            throw new IllegalStateException("Issue34 gate requires a new isolated loopback schema, explicit non-3306 port and non-root app user");
    }
    @DynamicPropertySource static void mysql(DynamicPropertyRegistry p) {
        guard();
        p.add("spring.sql.init.mode",()->"never");
        p.add("spring.datasource.url",()->required("MYSQL84_GATE_DB_URL"));
        p.add("spring.datasource.username",()->required("MYSQL84_GATE_DB_USERNAME"));
        p.add("spring.datasource.password",()->required("MYSQL84_GATE_DB_PASSWORD"));
        p.add("spring.datasource.driver-class-name",()->"com.mysql.cj.jdbc.Driver");
    }
    @Override void verifyIsolation(Connection connection) throws Exception {
        guard();
        Matcher match=URL.matcher(required("MYSQL84_GATE_DB_URL"));assertThat(match.matches()).isTrue();
        try(Statement statement=connection.createStatement(); ResultSet r=statement.executeQuery(
            "SELECT @@version,@@port,@@bind_address,@@transaction_isolation,DATABASE(),CURRENT_USER()")) {
            assertThat(r.next()).isTrue();assertThat(r.getString(1)).startsWith("8.4.");
            assertThat(r.getInt(2)).isEqualTo(Integer.parseInt(match.group("port")));
            assertThat(r.getString(3)).isEqualTo("127.0.0.1");assertThat(r.getString(4)).isEqualTo("REPEATABLE-READ");
            assertThat(r.getString(5)).isEqualTo(match.group("schema"));
            assertThat(r.getString(6)).startsWith(required("MYSQL84_GATE_DB_USERNAME")+"@");
            System.out.println("ISSUE34 MYSQL identity version="+r.getString(1)+" port="+r.getInt(2)+" isolation="+r.getString(4)+" schema="+r.getString(5));
        }
        deadlocksBefore=deadlocks();
    }
    Connection admin() throws Exception {
        return DriverManager.getConnection(required("MYSQL84_GATE_DB_URL"),required("MYSQL84_GATE_ADMIN_USERNAME"),required("MYSQL84_GATE_ADMIN_PASSWORD"));
    }
    long deadlocks() throws Exception {
        try(Connection c=admin();Statement s=c.createStatement();ResultSet r=s.executeQuery(
            "SELECT SUM(SUM_ERROR_RAISED) FROM performance_schema.events_errors_summary_global_by_error WHERE ERROR_NUMBER=1213")) {
            assertThat(r.next()).isTrue();return r.getLong(1);
        }
    }
    @Override void verifyWaiting(Future<Integer> second) throws Exception {
        try(Connection c=admin();Statement s=c.createStatement()) {
            long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
            while(System.nanoTime()<deadline) {
                try(ResultSet r=s.executeQuery("SELECT req.OBJECT_NAME,req.LOCK_TYPE,req.LOCK_MODE,w.REQUESTING_ENGINE_TRANSACTION_ID,w.BLOCKING_ENGINE_TRANSACTION_ID "
                    +"FROM performance_schema.data_lock_waits w JOIN performance_schema.data_locks req "
                    +"ON req.ENGINE_LOCK_ID=w.REQUESTING_ENGINE_LOCK_ID AND req.ENGINE=w.ENGINE "
                    +"WHERE req.OBJECT_SCHEMA=DATABASE() AND req.OBJECT_NAME IN ('floating_population','person')")) {
                    if(r.next()) {
                        assertThat(r.getString(4)).isNotEqualTo(r.getString(5));
                        assertThat(second.isDone()).isFalse();
                        System.out.println("ISSUE34 MYSQL lockWait table="+r.getString(1)+" type="+r.getString(2)+" mode="+r.getString(3)
                            +" requestingTx="+r.getString(4)+" blockingTx="+r.getString(5));return;
                    }
                }
                Thread.sleep(25);
            }
            throw new AssertionError("No actual InnoDB parent lock wait observed");
        }
    }
    @AfterEach void noMysqlDeadlocks() throws Exception {
        assertThat(deadlocks()).as("MySQL 1213 error count").isEqualTo(deadlocksBefore);
    }
    @Override void evidence(String scenario) {
        super.evidence("MYSQL RR "+scenario);
    }
}
