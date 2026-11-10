package com.wjx871.population.floating;

import static org.assertj.core.api.Assertions.*;
import java.sql.*;
import java.util.regex.*;
import org.junit.jupiter.api.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Explicit opt-in; the launcher owns a fresh private MySQL instance. */
class Issue37MySql84IT extends Issue37ExpiryBatchIntegrationTest {
    static String env(String name) {
        String value=System.getenv(name);
        if(value==null || value.isBlank()) throw new IllegalStateException("Missing "+name);
        return value;
    }
    static void guard() {
        String url=env("MYSQL84_GATE_DB_URL");
        if(!url.matches("jdbc:mysql://127\\.0\\.0\\.1:(?!3306/)[0-9]+/population_miniterm_mysql_gate_issue37_[a-z0-9_]+\\?.*createDatabaseIfNotExist=false.*")
            || env("MYSQL84_GATE_DB_USERNAME").equalsIgnoreCase("root")) throw new IllegalStateException("Private issue37 endpoint required");
    }
    @DynamicPropertySource static void mysql(DynamicPropertyRegistry p) {
        guard();
        p.add("spring.sql.init.mode",()->"never");
        p.add("spring.datasource.url",()->env("MYSQL84_GATE_DB_URL"));
        p.add("spring.datasource.username",()->env("MYSQL84_GATE_DB_USERNAME"));
        p.add("spring.datasource.password",()->env("MYSQL84_GATE_DB_PASSWORD"));
        p.add("spring.datasource.driver-class-name",()->"com.mysql.cj.jdbc.Driver");
        p.add("spring.datasource.hikari.transaction-isolation",()->"TRANSACTION_REPEATABLE_READ");
    }
    long deadlocksBefore;
    long deadlocks() throws Exception {
        try(Connection c=DriverManager.getConnection(env("MYSQL84_GATE_DB_URL"),env("MYSQL84_GATE_ADMIN_USERNAME"),env("MYSQL84_GATE_ADMIN_PASSWORD"));
            Statement s=c.createStatement(); ResultSet r=s.executeQuery("SELECT SUM(SUM_ERROR_RAISED) FROM performance_schema.events_errors_summary_global_by_error WHERE ERROR_NUMBER=1213")) {
            r.next(); return r.getLong(1);
        }
    }
    @BeforeEach void identity() throws Exception {
        guard(); deadlocksBefore=deadlocks();
        var identity=jdbc.queryForMap("SELECT @@version version,@@port port,@@transaction_isolation isolation_level,CURRENT_USER() account,DATABASE() schema_name");
        assertThat(identity.get("version").toString()).startsWith("8.4.");
        assertThat(identity.get("isolation_level")).isEqualTo("REPEATABLE-READ");
        assertThat(identity.get("account").toString()).startsWith(env("MYSQL84_GATE_DB_USERNAME")+"@");
        System.out.println("ISSUE37_MYSQL_IDENTITY "+identity);
    }
    @AfterEach void noDeadlock() throws Exception {
        long after=deadlocks();
        if(after!=deadlocksBefore) {
            try(Connection c=DriverManager.getConnection(env("MYSQL84_GATE_DB_URL"),env("MYSQL84_GATE_ADMIN_USERNAME"),env("MYSQL84_GATE_ADMIN_PASSWORD"));
                Statement s=c.createStatement();ResultSet r=s.executeQuery("SHOW ENGINE INNODB STATUS")) {
                r.next();System.out.println("ISSUE37_DEADLOCK_DIAGNOSTIC "+r.getString(3));
            }
        }
        assertThat(after).isEqualTo(deadlocksBefore);
    }
    @Override void verifyScannerWaiting(java.util.concurrent.Future<?> scan) throws Exception {
        try(Connection c=DriverManager.getConnection(env("MYSQL84_GATE_DB_URL"),env("MYSQL84_GATE_ADMIN_USERNAME"),env("MYSQL84_GATE_ADMIN_PASSWORD"));Statement s=c.createStatement()) {
            long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
            while(System.nanoTime()<deadline) {
                try(ResultSet r=s.executeQuery("SELECT req.OBJECT_NAME,req.LOCK_MODE,req.INDEX_NAME,w.REQUESTING_ENGINE_TRANSACTION_ID,w.BLOCKING_ENGINE_TRANSACTION_ID FROM performance_schema.data_lock_waits w JOIN performance_schema.data_locks req ON req.ENGINE_LOCK_ID=w.REQUESTING_ENGINE_LOCK_ID AND req.ENGINE=w.ENGINE WHERE req.OBJECT_SCHEMA=DATABASE() AND req.OBJECT_NAME IN ('person','floating_population')")) {
                    if(r.next()) {
                        assertThat(scan.isDone()).isFalse();
                        System.out.println("ISSUE37_LOCK_WAIT table="+r.getString(1)+" mode="+r.getString(2)+" index="+r.getString(3)+" requesting="+r.getString(4)+" blocking="+r.getString(5));
                        try(PreparedStatement held=c.prepareStatement("SELECT COUNT(*) FROM performance_schema.data_locks WHERE ENGINE_TRANSACTION_ID=? AND OBJECT_SCHEMA=DATABASE() AND OBJECT_NAME='residence_permit' AND LOCK_TYPE='RECORD' AND LOCK_MODE LIKE 'X%'")) {
                            held.setString(1,r.getString(4));
                            try(ResultSet locks=held.executeQuery()) {
                                locks.next();
                                assertThat(locks.getInt(1)).as("scanner has no permit X lock while waiting for parent").isZero();
                                System.out.println("ISSUE37_LOCK_ORDER heldPermitXWhileWaitingForParent=0");
                            }
                        }
                        return;
                    }
                }
                Thread.sleep(25);
            }
            throw new AssertionError("No actual InnoDB parent row wait observed");
        }
    }
    @Override void printPlans() {
        System.out.println("ISSUE37_INDEXES permit="+jdbc.queryForList("SHOW INDEX FROM residence_permit"));
        System.out.println("ISSUE37_INDEXES floating="+jdbc.queryForList("SHOW INDEX FROM floating_population"));
        super.printPlans();
    }
}
