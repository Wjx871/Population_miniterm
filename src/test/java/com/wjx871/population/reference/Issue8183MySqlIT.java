package com.wjx871.population.reference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.sql.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Explicit, new-schema-only MySQL 8.4 RR gate; default Maven tests never connect to MySQL. */
class Issue8183MySqlIT extends Issue8183HierarchyHttpTest {
    long deadlocksBefore;
    long timeoutsBefore;
    @DynamicPropertySource static void mysql(DynamicPropertyRegistry p) { Issue8183MySqlGate.configure(p); }
    @Override void verifyDatabase(Connection c) throws Exception {
        Issue8183MySqlGate.verify(c);
        assertThat(sql.getConfiguration().getDatabaseId()).isEqualTo("mysql");
        deadlocksBefore=errors(1213);timeoutsBefore=errors(1205);
    }
    long errors(int number) throws Exception {
        try(var c=Issue8183MySqlGate.admin();var s=c.prepareStatement(
                "SELECT COALESCE(SUM(SUM_ERROR_RAISED),0) FROM performance_schema.events_errors_summary_global_by_error WHERE ERROR_NUMBER=?")) {
            s.setInt(1,number);try(var r=s.executeQuery()){assertThat(r.next()).isTrue();return r.getLong(1);}
        }
    }
    @AfterEach void noDeadlocksOrTimeouts() throws Exception {
        assertThat(errors(1213)).as("MySQL deadlocks").isEqualTo(deadlocksBefore);
        assertThat(errors(1205)).as("MySQL lock wait timeouts").isEqualTo(timeoutsBefore);
        System.out.println("ISSUE8183 MYSQL deadlockDelta=0 timeoutDelta=0");
    }
    @Override void transactionEvidence() {
        super.transactionEvidence();
        String isolation=jdbc.queryForObject("SELECT @@transaction_isolation",String.class);
        assertThat(isolation).isEqualTo("REPEATABLE-READ");
        System.out.println("ISSUE8183 MYSQL activeServiceTransaction isolation="+isolation);
    }
    @Override void verifyWaiting(Future<?> f) throws Exception {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(4);
        try(var c=Issue8183MySqlGate.admin();var s=c.createStatement()) {
            while(System.nanoTime()<deadline) {
                try(var r=s.executeQuery("SELECT req.INDEX_NAME,req.LOCK_TYPE,req.LOCK_MODE,req.LOCK_DATA,"
                        +"w.REQUESTING_ENGINE_TRANSACTION_ID,w.BLOCKING_ENGINE_TRANSACTION_ID "
                        +"FROM performance_schema.data_lock_waits w JOIN performance_schema.data_locks req "
                        +"ON req.ENGINE_LOCK_ID=w.REQUESTING_ENGINE_LOCK_ID AND req.ENGINE=w.ENGINE "
                        +"WHERE req.OBJECT_SCHEMA=DATABASE() AND req.OBJECT_NAME='admin_region'")) {
                    if(r.next()) {
                        assertThat(f.isDone()).isFalse();assertThat(r.getString(5)).isNotEqualTo(r.getString(6));
                        System.out.println("ISSUE8183 MYSQL lockWait index="+r.getString(1)+" type="+r.getString(2)
                            +" mode="+r.getString(3)+" key="+r.getString(4)+" requestingTx="+r.getString(5)+" blockingTx="+r.getString(6));
                        return;
                    }
                }
                Thread.sleep(15);
            }
        }
        throw new AssertionError("No actual InnoDB admin_region lock wait observed");
    }
    @Test void siblingBranchesCanWriteWhileSharingRoot() throws Exception {
        String a=node("a",null,1),b=node("b",a,2),c=node("c",a,2),d=node("d",b,3),e=node("e",c,3);
        var held=new CountDownLatch(1);var release=new CountDownLatch(1);
        doAnswer(inv -> {
            AdminRegion value=inv.getArgument(0);
            if(value.getRegionCode().equals(d)) {
                transactionEvidence();held.countDown();
                if(!release.await(10,TimeUnit.SECONDS))throw new AssertionError("parallel fixture release timed out");
            }
            return realMapperCall(inv);
        }).when(mapper).update(any(),anyInt());
        var pool=Executors.newFixedThreadPool(2);
        try {
            var first=pool.submit(() -> move(d,b,3,0));assertThat(held.await(10,TimeUnit.SECONDS)).isTrue();
            var second=pool.submit(() -> move(e,c,3,0));
            check(second.get(5,TimeUnit.SECONDS),200);assertThat(first.isDone()).isFalse();
            release.countDown();check(first.get(10,TimeUnit.SECONDS),200);assertLegalHierarchy();
            System.out.println("ISSUE8183 MYSQL siblingBranches HTTP=200/200 secondCommittedBeforeFirstReleased=true rows="+snapshot());
        } finally {release.countDown();pool.shutdown();assertThat(pool.awaitTermination(20,TimeUnit.SECONDS)).isTrue();reset(mapper);}
    }
}
