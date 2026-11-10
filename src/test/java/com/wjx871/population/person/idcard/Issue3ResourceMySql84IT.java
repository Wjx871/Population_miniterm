package com.wjx871.population.person.idcard;
import static org.assertj.core.api.Assertions.assertThat;
import java.sql.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
/** Explicit opt-in: *IT is excluded from the normal H2 build. */
class Issue3ResourceMySql84IT extends Issue3ResourceObservationIntegrationTest {
    @DynamicPropertySource static void mysql(DynamicPropertyRegistry registry) { Issue3MySqlSupport.configure(registry); }
    @Test void positiveControlProvesTransactionAndLockProbesCanSeeAnActualOwner() throws Exception {
        try(Connection owner=DriverManager.getConnection(Issue3MySqlSupport.required("MYSQL84_GATE_DB_URL"),
                Issue3MySqlSupport.required("MYSQL84_GATE_DB_USERNAME"),Issue3MySqlSupport.required("MYSQL84_GATE_DB_PASSWORD"))) {
            owner.setAutoCommit(false);
            try {
                try(Statement sql=owner.createStatement();ResultSet row=sql.executeQuery("SELECT user_id FROM sys_user WHERE username='population' FOR UPDATE")) { assertThat(row.next()).isTrue(); }
                // innodb_trx is cached for a short interval by MySQL; poll to observe the controlled transaction.
                java.util.Map<String,Long> visible=java.util.Map.of();
                for(int i=0;i<15;i++) { visible=databaseSnapshot(); if(visible.get("innodbTransactions")>0) break; Thread.sleep(100); }
                assertThat(visible.get("innodbTransactions")).isPositive();
                assertThat(visible.get("activeTransactions")).isPositive();
                assertThat(visible.get("dataLocks")).isPositive();
                System.out.println("ISSUE3_PROBE_POSITIVE_CONTROL "+visible);
                // Show actual lock types and modes, never key values or personal data.
                try(Connection observer=monitor();PreparedStatement sql=observer.prepareStatement("SELECT l.LOCK_TYPE,l.LOCK_MODE,l.LOCK_STATUS,COUNT(*) FROM performance_schema.data_locks l JOIN performance_schema.threads t ON l.THREAD_ID=t.THREAD_ID WHERE t.PROCESSLIST_USER=? GROUP BY l.LOCK_TYPE,l.LOCK_MODE,l.LOCK_STATUS")) {
                    sql.setString(1,System.getenv("MYSQL84_GATE_DB_USERNAME"));
                    try(ResultSet r=sql.executeQuery()) { while(r.next()) System.out.printf("ISSUE3_CONTROL_LOCK type=%s mode=%s status=%s count=%d%n",r.getString(1),r.getString(2),r.getString(3),r.getLong(4)); }
                }
            } finally { owner.rollback(); }
        }
        Thread.sleep(150);
        assertThat(databaseSnapshot().values()).allSatisfy(value->assertThat(value).isZero());
        System.out.println("ISSUE3_PROBE_POSITIVE_CONTROL_ROLLBACK noRemainingTransactionsOrLocks=true");
    }
}