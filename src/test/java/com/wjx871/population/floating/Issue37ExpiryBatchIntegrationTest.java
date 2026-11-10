package com.wjx871.population.floating;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.session.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Runs unchanged against the original implementation to capture the unbounded RED. */
@SpringBootTest(properties = "residence-permit.expiry-batch-size=3")
@AutoConfigureMockMvc
@Import(Issue37ExpiryBatchIntegrationTest.ProbeConfig.class)
class Issue37ExpiryBatchIntegrationTest {
    static final LocalDate TODAY = LocalDate.of(2026, 7, 11);
    @TestConfiguration static class ProbeConfig {
        @Bean @Primary Clock issue37Clock() {
            return Clock.fixed(TODAY.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        }
        @Bean CandidateProbe candidateProbe() { return new CandidateProbe(); }
    }
    @Intercepts(@Signature(type=Executor.class, method="query", args={MappedStatement.class,Object.class,RowBounds.class,ResultHandler.class}))
    static class CandidateProbe implements Interceptor {
        final List<Integer> sizes = new CopyOnWriteArrayList<>();
        final List<String> sql = new CopyOnWriteArrayList<>();
        volatile Runnable afterRead;
        volatile java.util.function.BiConsumer<String,List<?>> onRead;
        @Override public Object intercept(Invocation invocation) throws Throwable {
            Object result = invocation.proceed();
            MappedStatement ms = (MappedStatement) invocation.getArgs()[0];
            if (ms.getId().contains(".expired")) {
                List<?> rows = (List<?>) result;
                sizes.add(rows.size());
                String statement = ms.getBoundSql(invocation.getArgs()[1]).getSql().replaceAll("\\s+", " ");
                sql.add(statement);
                System.out.println("ISSUE37_CANDIDATE statement=" + ms.getId() + " rows=" + rows.size() + " sql=" + statement);
                var observer=onRead;
                if(observer!=null) observer.accept(ms.getId(),rows);
                Runnable callback = afterRead;
                if (callback != null && !rows.isEmpty()) { afterRead=null; callback.run(); }
            }
            return result;
        }
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired ResidencePermitExpiryService expiry;
    @Autowired CandidateProbe probe;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired org.springframework.transaction.PlatformTransactionManager tx;
    @MockBean FloatingResidenceTransactionHook hook;
    @SpyBean ResidencePermitDatePolicy dates;
    // Every fixture owns a fresh range, so the suite never deletes existing data.
    static long next = 370000;
    long base;
    int permitOperationsBefore,floatingOperationsBefore;
    int operations(String type) {return jdbc.queryForObject("SELECT COUNT(*) FROM operation_log WHERE operation_type=?",Integer.class,type);}
    @BeforeEach void before() {
        reset(hook,dates); probe.sizes.clear(); probe.sql.clear(); probe.afterRead=null; probe.onRead=null; base=next; next+=30000;
        permitOperationsBefore=operations("RESIDENCE_PERMIT_EXPIRE");
        floatingOperationsBefore=operations("FLOATING_REGISTRATION_EXPIRE");
    }
    @AfterEach void retainFixtureAsHistory() {
        probe.afterRead=null;
        probe.onRead=null;
        jdbc.update("UPDATE residence_permit SET status='CANCELLED',current_flag=NULL WHERE permit_id>? AND permit_id<=? AND status='ACTIVE'",base,base+30000);
        jdbc.update("UPDATE floating_population SET status='CANCELLED',current_flag=NULL WHERE floating_id>? AND floating_id<=? AND status='ACTIVE'",base,base+30000);
    }
    void seed(int n, boolean expiredPermit, boolean expiredFloating) {
        System.out.println("ISSUE37_FIXTURE base="+base+" count="+n+" expiredPermit="+expiredPermit+" expiredFloating="+expiredFloating+" today="+TODAY);
        new org.springframework.transaction.support.TransactionTemplate(tx).executeWithoutResult(transaction -> {
        for (int i=1; i<=n; i++) {
            long id=base+i;
            jdbc.update("INSERT INTO person(person_id,name,gender,id_card,status,current_status_code) VALUES(?,'synthetic-37','1',?,'正常','MIGRATED_OUT')",id,String.format("%018d",id));
            jdbc.update("INSERT INTO business_application(application_id,application_no,business_type,title,applicant_user_id,target_person_id,reason,status,version) VALUES(?,?,'FLOATING_REGISTRATION','synthetic-37',6,?,'test','COMPLETED',0)",id,"I37-"+id,id);
            jdbc.update("INSERT INTO floating_population(floating_id,registration_no,source_application_id,person_id,source_region_code,current_region_code,current_address,residence_reason_code,residence_proof_type,arrival_date,planned_leave_date,registration_date,status,current_flag,version) VALUES(?,?,?,?,'370100','110000','synthetic','EMPLOYMENT','RENTAL_CONTRACT','2026-01-01',?,'2026-01-01','ACTIVE',1,0)",id,"I37F-"+id,id,id,expiredFloating?TODAY.minusDays(1):TODAY.plusDays(100));
            jdbc.update("INSERT INTO residence_permit(permit_id,permit_no,person_id,floating_id,source_application_id,issue_region_code,issuing_authority,issue_date,valid_from,valid_until,status,current_flag,version) VALUES(?,?,?,?,?,'110000','synthetic','2026-01-01','2026-01-01',?,'ACTIVE',1,0)",id,"I37P-"+id,id,id,id,expiredPermit?TODAY.minusDays(1):TODAY.plusDays(100));
        }
        });
        try {
            var snapshot=java.nio.file.Path.of(System.getProperty("java.io.tmpdir"),"issue37-fixture-"+UUID.randomUUID()+".csv");
            var rows=new ArrayList<String>();
            rows.add("id,permit_status,permit_current,valid_until,floating_status,floating_current,planned_leave_date");
            for(int i=1;i<=n;i++) rows.add((base+i)+",ACTIVE,1,"+(expiredPermit?TODAY.minusDays(1):TODAY.plusDays(100))+",ACTIVE,1,"+(expiredFloating?TODAY.minusDays(1):TODAY.plusDays(100)));
            java.nio.file.Files.write(snapshot,rows,java.nio.file.StandardOpenOption.CREATE_NEW);
            System.out.println("ISSUE37_FIXTURE_SNAPSHOT "+snapshot);
        } catch(java.io.IOException e) { throw new IllegalStateException(e); }
    }
    int count(String table, String condition) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM "+table+" WHERE "+(table.equals("floating_population")?"floating_id":"permit_id")+">? AND "+(table.equals("floating_population")?"floating_id":"permit_id")+"<=? AND "+condition,Integer.class,base,base+30000);
    }
    @ParameterizedTest @ValueSource(ints={0,2,3,4,11})
    void permitPagesAreBoundedAndComplete(int n) {
        seed(n,true,false);
        assertThat(expiry.runNow()).isEqualTo(n);
        assertThat(count("residence_permit","status='EXPIRED'")).isEqualTo(n);
        assertThat(probe.sizes).allMatch(size->size<=3);
        assertThat(probe.sizes).hasSize(n/3+2); // permits: floor(n/3)+1; floating: empty
        assertThat(expiry.runNow()).isZero();
        assertThat(count("residence_permit_log","action='EXPIRE'")).isEqualTo(n);
        assertThat(operations("RESIDENCE_PERMIT_EXPIRE")-permitOperationsBefore).isEqualTo(n);
    }
    @Test void floatingPagesAreBoundedAndHistoryAndLinkedPermitLogsSurvive() {
        seed(11,false,true);
        assertThat(expiry.runNow()).isEqualTo(11);
        assertThat(count("floating_population","status='EXPIRED'")).isEqualTo(11);
        assertThat(count("residence_permit","status='EXPIRED'")).isEqualTo(11);
        assertThat(count("residence_permit_log","action='REGISTRATION_CLOSED'")).isEqualTo(11);
        assertThat(probe.sizes).allMatch(size->size<=3);
        assertThat(expiry.runNow()).isZero();
        assertThat(operations("FLOATING_REGISTRATION_EXPIRE")-floatingOperationsBefore).isEqualTo(11);
    }
    @Test void validAndTerminalAndHistoricalRecordsArePreserved() {
        seed(4,false,false);
        jdbc.update("UPDATE residence_permit SET valid_until=?,status=CASE WHEN permit_id=? THEN 'EXPIRED' WHEN permit_id=? THEN 'CANCELLED' ELSE status END,current_flag=NULL WHERE permit_id>? AND permit_id<=?",TODAY.minusDays(1),base+1,base+2,base,base+4);
        jdbc.update("UPDATE floating_population SET planned_leave_date=?,current_flag=NULL WHERE floating_id>? AND floating_id<=?",TODAY.minusDays(1),base,base+4);
        assertThat(expiry.runNow()).isZero();
        assertThat(count("residence_permit_log","1=1")).isZero();
    }
    @Test void futureDatesAreNotCandidates() {
        seed(2,false,false);
        assertThat(expiry.runNow()).isZero();
        assertThat(count("residence_permit","status='ACTIVE'")).isEqualTo(2);
    }
    @Test void candidateExtendedBeforeLockIsPreserved() {
        seed(1,true,false);
        probe.afterRead=()->jdbc.update("UPDATE residence_permit SET valid_until=?,version=version+1 WHERE permit_id=?",TODAY.plusDays(365),base+1);
        assertThat(expiry.runNow()).isOne(); // candidate count, not successful updates
        assertThat(count("residence_permit","status='ACTIVE'")).isOne();
        assertThat(count("residence_permit_log","1=1")).isZero();
    }
    @Test void plannedLeaveChangedBeforeLockIsPreserved() {
        seed(1,false,true);
        probe.afterRead=()->jdbc.update("UPDATE floating_population SET planned_leave_date=?,version=version+1 WHERE floating_id=?",TODAY.plusDays(365),base+1);
        assertThat(expiry.runNow()).isOne();
        assertThat(count("floating_population","status='ACTIVE'")).isOne();
        assertThat(count("residence_permit","status='ACTIVE'")).isOne();
    }
    @Test void oneBusinessDateIsFrozenAcrossMidnight() {
        seed(1,false,false);
        jdbc.update("UPDATE residence_permit SET valid_until=? WHERE permit_id=?",TODAY,base+1);
        jdbc.update("UPDATE floating_population SET planned_leave_date=? WHERE floating_id=?",TODAY,base+1);
        doReturn(TODAY,TODAY.plusDays(1)).when(dates).today();
        assertThat(expiry.runNow()).isZero();
        assertThat(count("residence_permit","status='ACTIVE'")).isOne();
        assertThat(count("floating_population","status='ACTIVE'")).isOne();
        verify(dates,times(1)).today();
    }
    @Test void candidateLosingCurrentFlagBeforePermitLockIsPreserved() {
        seed(1,true,false);
        probe.afterRead=()->jdbc.update("UPDATE residence_permit SET current_flag=NULL WHERE permit_id=?",base+1);
        assertThat(expiry.runNow()).isOne();
        assertThat(count("residence_permit","status='ACTIVE'")).isOne();
    }
    @Test void candidateLosingCurrentFlagBeforeFloatingLockIsPreserved() {
        seed(1,false,true);
        probe.afterRead=()->jdbc.update("UPDATE floating_population SET current_flag=NULL WHERE floating_id=?",base+1);
        assertThat(expiry.runNow()).isOne();
        assertThat(count("floating_population","status='ACTIVE'")).isOne();
        assertThat(count("residence_permit","status='ACTIVE'")).isOne();
    }
    @Test void singleFailureRollsBackOnlyItsTransactionAndRerunCompletes() {
        seed(4,true,false);
        AtomicBoolean first=new AtomicBoolean(true);
        doAnswer(inv->{if(!first.getAndSet(false)) throw new IllegalStateException("issue37-rollback"); return null;}).when(hook).afterPermitExpire();
        assertThatThrownBy(expiry::runNow).isInstanceOf(IllegalStateException.class);
        assertThat(count("residence_permit","status='EXPIRED'")).isOne();
        assertThat(count("residence_permit_log","action='EXPIRE'")).isOne();
        assertThat(operations("RESIDENCE_PERMIT_EXPIRE")-permitOperationsBefore).isOne();
        reset(hook);
        assertThat(expiry.runNow()).isEqualTo(3);
        assertThat(count("residence_permit_log","action='EXPIRE'")).isEqualTo(4);
        assertThat(operations("RESIDENCE_PERMIT_EXPIRE")-permitOperationsBefore).isEqualTo(4);
    }
    @Test void twoScannersWriteExactlyOneSuccessfulTransition() throws Exception {
        seed(11,true,true);
        CyclicBarrier bothReadSamePage=new CyclicBarrier(2);
        var reads=new java.util.concurrent.atomic.AtomicInteger();
        probe.onRead=(statement,rows)->{
            if(statement.endsWith(".expiredPermitIds") && !rows.isEmpty() && reads.incrementAndGet()<=2) {
                try {bothReadSamePage.await(10,TimeUnit.SECONDS);} catch(Exception e) {throw new IllegalStateException(e);}
            }
        };
        ExecutorService pool=Executors.newFixedThreadPool(2);
        try {
            Future<Integer> a=pool.submit(expiry::runNow),b=pool.submit(expiry::runNow);
            int first=a.get(30,TimeUnit.SECONDS),second=b.get(30,TimeUnit.SECONDS);
            assertThat(first).isGreaterThanOrEqualTo(3);
            assertThat(second).isGreaterThanOrEqualTo(3);
            assertThat(count("residence_permit_log","action='EXPIRE'")).isEqualTo(11);
            assertThat(count("floating_population","status='EXPIRED'")).isEqualTo(11);
            System.out.println("ISSUE37_DUAL sameFirstPage=true scannerCandidates="+first+","+second+" successfulPermitTransitions=11 successfulFloatingTransitions=11");
        } finally { pool.shutdownNow(); }
    }
    String auth(String user) throws Exception {
        String body=mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\""+user+"\",\"password\":\"123456\"}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer "+json.readTree(body).path("data").path("token").asText();
    }
    long approvedEndorsement(long permit) throws Exception {
        String applicant=auth("population");
        String body=mvc.perform(post("/api/residence-permits/applications/"+permit+"/endorsement")
            .header("Authorization",applicant).contentType(MediaType.APPLICATION_JSON)
            .content("{\"title\":\"synthetic37\",\"reason\":\"synthetic37\"}"))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long application=json.readTree(body).path("data").path("applicationId").asLong();
        for(String type:List.of("CURRENT_RESIDENCE_PERMIT","CONTINUED_RESIDENCE_PROOF")) {
            String file=UUID.randomUUID()+".pdf";
            jdbc.update("INSERT INTO application_material(application_id,material_type,material_name,original_filename,stored_filename,storage_path,content_type,file_size,file_sha256,required_flag,verify_status,verify_user_id,uploaded_by) VALUES(?,?,?,'synthetic.pdf',?,?,'application/pdf',1,?,1,'VERIFIED',7,6)",application,type,type,file,"test",UUID.randomUUID().toString().replace("-","")+UUID.randomUUID().toString().replace("-",""));
        }
        mvc.perform(post("/api/applications/"+application+"/submit").header("Authorization",applicant)).andExpect(status().isOk());
        long approval=jdbc.queryForObject("SELECT approval_id FROM sys_approval_request WHERE application_id=?",Long.class,application);
        mvc.perform(post("/api/approvals/"+approval+"/approve").header("Authorization",auth("approver"))
            .contentType(MediaType.APPLICATION_JSON).content("{\"comment\":\"synthetic37\",\"version\":0}"))
            .andExpect(status().isOk());
        return application;
    }
    @Test void actualEndorsementCommitsBetweenCandidateReadAndWorkerLock() throws Exception {
        seed(1,true,false);
        long application=approvedEndorsement(base+1);
        String executor=auth("household");
        CountDownLatch read=new CountDownLatch(1),release=new CountDownLatch(1);
        probe.afterRead=()->{read.countDown();try {if(!release.await(15,TimeUnit.SECONDS))throw new IllegalStateException("candidate pause timeout");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}};
        ExecutorService pool=Executors.newSingleThreadExecutor();
        try {
            Future<Integer> scan=pool.submit(expiry::runNow);
            assertThat(read.await(10,TimeUnit.SECONDS)).isTrue();
            mvc.perform(post("/api/residence-permits/applications/"+application+"/endorse")
                .header("Authorization",executor).contentType(MediaType.APPLICATION_JSON).content("{\"version\":2}"))
                .andExpect(status().isOk());
            release.countDown();
            assertThat(scan.get(20,TimeUnit.SECONDS)).isOne();
            assertThat(count("residence_permit","status='ACTIVE'")).isOne();
            assertThat(count("residence_permit_log","action='ENDORSE'")).isOne();
            assertThat(count("residence_permit_log","action='EXPIRE'")).isZero();
        } finally {release.countDown();pool.shutdownNow();}
    }
    void verifyScannerWaiting(Future<?> scan) throws Exception { assertThat(scan.isDone()).isFalse(); }
    @Test void scanInterleavesWithActualCloseWithoutDeadlock() throws Exception {
        seed(1,true,true);
        String token=auth("household");
        CountDownLatch locked=new CountDownLatch(1),release=new CountDownLatch(1),candidateRead=new CountDownLatch(1);
        doAnswer(inv->{locked.countDown(); if(!release.await(10,TimeUnit.SECONDS)) throw new IllegalStateException("close timeout");return null;}).when(hook).afterFloatingClose();
        probe.afterRead=candidateRead::countDown;
        ExecutorService pool=Executors.newFixedThreadPool(2);
        try {
            Future<Integer> close=pool.submit(()->mvc.perform(post("/api/floating-populations/"+(base+1)+"/close")
                .header("Authorization",token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reasonCode\":\"LEFT_REGION\",\"comment\":\"synthetic37\",\"version\":0}"))
                .andReturn().getResponse().getStatus());
            assertThat(locked.await(10,TimeUnit.SECONDS)).isTrue();
            Future<Integer> scan=pool.submit(expiry::runNow);
            assertThat(candidateRead.await(5,TimeUnit.SECONDS)).isTrue();
            verifyScannerWaiting(scan);
            release.countDown();
            int closeStatus=close.get(20,TimeUnit.SECONDS);
            assertThat(closeStatus).isEqualTo(200);
            scan.get(20,TimeUnit.SECONDS);
            assertThat(count("floating_population","status='LEFT'")).isOne();
            assertThat(count("residence_permit","status='CANCELLED'")).isOne();
            assertThat(count("residence_permit_log","1=1")).isOne();
        } finally { release.countDown(); pool.shutdownNow(); }
    }
    @Test @org.junit.jupiter.api.condition.EnabledIfSystemProperty(named="issue37.large", matches="true")
    void tenThousandCandidatesPerKindHaveBoundedResults() {
        seed(10000,true,true);
        printPlans();
        long started=System.nanoTime();
        assertThat(expiry.runNow()).isEqualTo(20000);
        long elapsed=(System.nanoTime()-started)/1_000_000;
        assertThat(count("residence_permit","status='EXPIRED'")).isEqualTo(10000);
        assertThat(count("floating_population","status='EXPIRED'")).isEqualTo(10000);
        assertThat(probe.sizes).allMatch(size->size<=3);
        assertThat(probe.sizes.stream().mapToInt(Integer::intValue).sum()).isEqualTo(20000);
        System.out.println("ISSUE37_LARGE candidates=20000 queries="+probe.sizes.size()+" peak="+Collections.max(probe.sizes)+" elapsedMs="+elapsed);
        assertThat(expiry.runNow()).isZero();
    }
    void printPlans() {
        for (String table : List.of("residence_permit","floating_population")) {
            String id=table.equals("residence_permit")?"permit_id":"floating_id";
            String date=table.equals("residence_permit")?"valid_until":"planned_leave_date";
            for (long cursor : new long[]{0,base+5000,base+9999}) {
                String sql="SELECT "+id+" FROM "+table+" WHERE status='ACTIVE' AND current_flag=1 AND "+date+"<'2026-07-11' AND "+id+">"+cursor+" ORDER BY "+id+" LIMIT 3";
                System.out.println("ISSUE37_EXPLAIN sql="+sql+" plan="+jdbc.queryForList("EXPLAIN "+sql));
            }
        }
    }
}
