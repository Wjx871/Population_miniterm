package com.wjx871.population.floating;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.wjx871.population.application.BusinessType;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.*;

class Issue34FirstIssueExclusivityTest extends Issue34HttpSupport {
    @Test void eligiblePersonCreatesLinkedDraft() throws Exception {
        long f=floating(9340001);
        long app=applicationId(first(f,"population"));
        assertThat(jdbc.queryForMap("SELECT person_id,floating_id,apply_type FROM residence_permit_application WHERE application_id=?",app))
            .containsEntry("person_id",9340001L).containsEntry("floating_id",f).containsEntry("apply_type","FIRST_ISSUE");
        assertThat(state(app)).isEqualTo("DRAFT");
    }
    @ParameterizedTest @ValueSource(strings={"DRAFT","SUBMITTED","UNDER_REVIEW","APPROVED","RETURNED","RESUBMITTED"})
    void authoritativeOpenStatesRejectWithoutSideEffects(String state) throws Exception {
        long f=floating(9340001),app=applicationId(first(f,"population"));
        // State fixtures deliberately leave the professional state stale (DRAFT).
        jdbc.update("UPDATE business_application SET status=? WHERE application_id=?",state,app);
        List<Integer> before=snapshot();
        ResponseEntity<String> duplicate=first(f,"population");
        check(duplicate,409);
        assertThat(tree(duplicate).path("message").asText()).isEqualTo("该人员已有处理中首次申领申请");
        assertThat(snapshot()).isEqualTo(before);
        evidence("GREEN open="+state+" HTTP=409 noSideEffects=true");
    }
    @ParameterizedTest @ValueSource(strings={"REJECTED","WITHDRAWN","CANCELLED","COMPLETED"})
    void terminalGenericStateReleasesSlotDespiteStaleProfessionalDraft(String state) throws Exception {
        long f=floating(9340001),app=applicationId(first(f,"population"));
        jdbc.update("UPDATE business_application SET status=? WHERE application_id=?",state,app);
        check(first(f,"population"),201);
        assertThat(count("residence_permit_application")).isEqualTo(2);
        evidence("GREEN terminal="+state+" HTTP=201");
    }
    @Test void realDraftCancellationReleasesSlot() throws Exception {
        long f=floating(9340001),app=applicationId(first(f,"population"));
        check(request("DELETE","/api/applications/"+app,"population",null),200);
        assertThat(state(app)).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("SELECT business_status FROM residence_permit_application WHERE application_id=?",String.class,app)).isEqualTo("DRAFT");
        check(first(f,"population"),201);
    }
    @Test void realRejectionAndWithdrawalReleaseSlot() throws Exception {
        long f=floating(9340001),app=applicationId(first(f,"population"));
        materials(app,true);submit(app);
        long approval=jdbc.queryForObject("SELECT approval_id FROM sys_approval_request WHERE application_id=?",Long.class,app);
        check(post("/api/approvals/"+approval+"/reject","approver",Map.of("comment","rejected","version",0)),200);
        assertThat(state(app)).isEqualTo("REJECTED");
        long second=applicationId(first(f,"population"));
        materials(second,true);submit(second);
        check(post("/api/applications/"+second+"/withdraw","population",null),200);
        assertThat(state(second)).isEqualTo("WITHDRAWN");
        check(first(f,"population"),201);
    }
    @Test void reviewApprovalReturnResubmitAndExplicitIssueRemainUsable() throws Exception {
        long f=floating(9340001),app=applicationId(first(f,"population"));
        // Real multipart upload, then real material verification; remaining materials are synthetic fixtures.
        materials(app,true);
        long material=uploadExtraMaterial(app);
        submit(app);
        check(post("/api/materials/"+material+"/verify","approver",Map.of("result","VERIFIED","comment","checked")),200);
        assertNoDuplicate(f);
        approve(app);assertNoDuplicate(f);
        assertThat(count("residence_permit")).isZero();
        returned(app);assertThat(state(app)).isEqualTo("RETURNED");assertNoDuplicate(f);
        submit(app);approve(app);
        int detailVersion=jdbc.queryForObject("SELECT version FROM residence_permit_application WHERE application_id=?",Integer.class,app);
        check(post("/api/residence-permits/applications/"+app+"/issue","household",Map.of("issuingAuthority","Issue34 authority","version",detailVersion)),200);
        assertThat(state(app)).isEqualTo("COMPLETED");
        List<Integer> before=snapshot();
        ResponseEntity<String> duplicate=first(f,"population");check(duplicate,409);
        assertThat(tree(duplicate).path("message").asText()).isEqualTo("该人员已有当前有效居住证");
        assertThat(snapshot()).isEqualTo(before);
        // COMPLETED is free after the formerly issued permit ceases to be ACTIVE.
        jdbc.update("UPDATE residence_permit SET status='EXPIRED',current_flag=NULL");
        check(first(f,"population"),201);
    }
    @Test void differentPeopleCanEachCreate() throws Exception {
        long a=floating(9340001),b=floating(9340002);
        check(first(a,"population"),201);check(first(b,"population"),201);
        assertThat(count("residence_permit_application")).isEqualTo(2);
    }
    @ParameterizedTest @ValueSource(strings={"admin","household"})
    void differentAuthorizedRequestSourcesStillConflict(String user) throws Exception {
        long f=floating(9340001);applicationId(first(f,"population"));
        List<Integer> before=snapshot();check(first(f,user),409);assertThat(snapshot()).isEqualTo(before);
    }
    @Test void historicalFloatingIdDoesNotReleasePersonsOpenApplication() throws Exception {
        long old=floating(9340001);applicationId(first(old,"population"));
        check(post("/api/floating-populations/"+old+"/close","household",Map.of("reasonCode","LEFT_REGION","comment","left","version",0)),200);
        long current=floating(9340001);assertThat(current).isNotEqualTo(old);
        assertNoDuplicate(current);
        assertThat(count("residence_permit_application")).isOne();
    }
    @Test void otherPermitBusinessTypesDoNotOccupyFirstIssueSlot() throws Exception {
        long f=floating(9340001),app=applicationId(first(f,"population"));
        jdbc.update("UPDATE business_application SET business_type='RESIDENCE_PERMIT_ENDORSEMENT' WHERE application_id=?",app);
        jdbc.update("UPDATE residence_permit_application SET apply_type='ENDORSEMENT' WHERE application_id=?",app);
        long second=applicationId(first(f,"population"));
        jdbc.update("UPDATE business_application SET business_type='RESIDENCE_PERMIT_CANCELLATION' WHERE application_id=?",second);
        jdbc.update("UPDATE residence_permit_application SET apply_type='CANCELLATION' WHERE application_id=?",second);
        check(first(f,"population"),201);
    }
    @Test void incompleteHistoricalGenericApplicationStillOccupiesSlot() throws Exception {
        long f=floating(9340001),app=applicationId(first(f,"population"));
        jdbc.update("DELETE FROM residence_permit_application WHERE application_id=?",app);
        assertNoDuplicate(f);
    }
    @Test void historicalDuplicatesArePreservedAndBlockAnotherCreation() throws Exception {
        long f=floating(9340001),first=applicationId(first(f,"population"));
        jdbc.update("UPDATE business_application SET status='CANCELLED' WHERE application_id=?",first);
        applicationId(first(f,"population"));
        jdbc.update("UPDATE business_application SET status='DRAFT' WHERE application_id=?",first);
        assertNoDuplicate(f);
        assertThat(count("residence_permit_application")).isEqualTo(2);
    }
    @Test void scopeFailureDoesNotDiscloseExistingApplication() throws Exception {
        long f=floating(9340001);applicationId(first(f,"population"));
        jdbc.update("UPDATE floating_population SET current_region_code='120000' WHERE floating_id=?",f);
        List<Integer> before=snapshot();
        ResponseEntity<String> response=first(f,"population");check(response,403);
        assertThat(tree(response).path("message").asText()).isEqualTo("无权访问该业务数据");
        assertThat(response.getBody()).doesNotContain("处理中", "applicationId", "applicant");
        check(first(f,"admin"),409);
        assertThat(snapshot()).isEqualTo(before);
    }
    @Test void selfAndDepartmentScopeAreEnforcedBeforeExclusivity() throws Exception {
        long f=floating(9340001);applicationId(first(f,"population"));
        long role=jdbc.queryForObject("SELECT role_id FROM sys_user WHERE username='population'",Long.class);
        try {
            jdbc.update("UPDATE sys_role SET data_scope='SELF' WHERE role_id=?",role);
            assertNoDuplicate(f);
            jdbc.update("UPDATE business_application SET applicant_user_id=(SELECT user_id FROM sys_user WHERE username='admin') WHERE application_id=(SELECT source_application_id FROM floating_population WHERE floating_id=?)",f);
            check(first(f,"population"),403);
            jdbc.update("UPDATE sys_role SET data_scope='DEPARTMENT' WHERE role_id=?",role);
            check(first(f,"population"),403); // executed registration belongs to household's department
        } finally { jdbc.update("UPDATE sys_role SET data_scope='REGION' WHERE role_id=?",role); }
    }
    @RepeatedTest(10) void repeatedConcurrentHttpCreatesExactlyOneDraft(RepetitionInfo repetition) throws Exception {
        long f=floating(9340001);
        List<Integer> before=snapshot();
        List<Integer> result=race(f,false);
        assertThat(result).containsExactlyInAnyOrder(201,409);
        assertThat(count("business_application")).isEqualTo(before.get(0)+1);
        assertThat(count("residence_permit_application")).isOne();
        assertThat(count("sys_approval_request")).isEqualTo(before.get(2));
        assertThat(count("sys_approval_log")).isEqualTo(before.get(3)+1);
        assertThat(count("operation_log")).isEqualTo(before.get(4));
        evidence("GREEN concurrency round="+repetition.getCurrentRepetition()+" HTTP="+result+" success=1 conflict=1");
    }
    @Test void firstTransactionRollbackLetsWaitingRequestCreate() throws Exception {
        long f=floating(9340001);List<Integer> before=snapshot();
        assertThat(race(f,true)).containsExactly(500,201);
        assertThat(count("business_application")).isEqualTo(before.get(0)+1);
        assertThat(count("residence_permit_application")).isOne();
        assertThat(count("sys_approval_log")).isEqualTo(before.get(3)+1);
        assertThat(count("sys_approval_request")).isEqualTo(before.get(2));
        assertThat(count("operation_log")).isEqualTo(before.get(4));
        evidence("GREEN rollback HTTP=500,201 committedDrafts=1");
    }
    @Test void differentPeopleCanCreateConcurrentlyWithoutGlobalOrGapLocks() throws Exception {
        long a=floating(9340001),b=floating(9340002);
        CountDownLatch both=new CountDownLatch(2);
        doAnswer(inv->{
            Object result=inv.callRealMethod();
            if(inv.getArgument(0,com.wjx871.population.application.ApplicationRequest.class).businessType()==BusinessType.RESIDENCE_PERMIT_FIRST_ISSUE) {
                both.countDown();assertThat(both.await(8,TimeUnit.SECONDS)).isTrue();
            }
            return result;
        }).when(applications).createSpecialized(any(),any());
        ExecutorService pool=Executors.newFixedThreadPool(2);
        try {
            Future<Integer> one=pool.submit(()->first(a,"population").getStatusCode().value());
            Future<Integer> two=pool.submit(()->first(b,"admin").getStatusCode().value());
            assertThat(List.of(one.get(15,TimeUnit.SECONDS),two.get(15,TimeUnit.SECONDS))).containsExactly(201,201);
        } finally { pool.shutdownNow();assertThat(pool.awaitTermination(5,TimeUnit.SECONDS)).isTrue(); }
    }
    List<Integer> race(long f, boolean rollback) throws Exception {
        CountDownLatch inserted=new CountDownLatch(1),release=new CountDownLatch(1);
        AtomicInteger creates=new AtomicInteger();
        doAnswer(inv->{
            Object result=inv.callRealMethod();
            if(inv.getArgument(0,com.wjx871.population.application.ApplicationRequest.class).businessType()==BusinessType.RESIDENCE_PERMIT_FIRST_ISSUE && creates.getAndIncrement()==0) {
                inserted.countDown();
                if(!release.await(10,TimeUnit.SECONDS))throw new IllegalStateException("Issue34 first release timeout");
                if(rollback)throw new IllegalStateException("Issue34 injected rollback after generic insert");
            }
            return result;
        }).when(applications).createSpecialized(any(),any());
        ExecutorService pool=Executors.newFixedThreadPool(2);
        try {
            Future<Integer> one=pool.submit(()->first(f,"population").getStatusCode().value());
            assertThat(inserted.await(8,TimeUnit.SECONDS)).isTrue();
            Future<Integer> two=pool.submit(()->first(f,"admin").getStatusCode().value());
            verifyWaiting(two);
            release.countDown();
            return List.of(one.get(20,TimeUnit.SECONDS),two.get(20,TimeUnit.SECONDS));
        } finally { release.countDown();pool.shutdownNow();assertThat(pool.awaitTermination(5,TimeUnit.SECONDS)).isTrue(); }
    }
    void verifyWaiting(Future<Integer> second) throws Exception {
        Thread.sleep(150);assertThat(second.isDone()).as("second request waits on the parent row").isFalse();
    }
    void assertNoDuplicate(long f) throws Exception {
        List<Integer> before=snapshot();check(first(f,"population"),409);assertThat(snapshot()).isEqualTo(before);
    }
    long uploadExtraMaterial(long app) throws Exception {
        org.springframework.util.MultiValueMap<String,Object> parts=new org.springframework.util.LinkedMultiValueMap<>();
        parts.add("materialType","OTHER");parts.add("materialName","Issue34 HTTP upload");
        parts.add("file",new org.springframework.core.io.ByteArrayResource("%PDF-1.4\nIssue34 synthetic PDF\n%%EOF".getBytes(java.nio.charset.StandardCharsets.UTF_8)) {
            @Override public String getFilename(){return "issue34.pdf";}
        });
        HttpHeaders headers=new HttpHeaders();headers.setBearerAuth(tokens.get("population"));headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        ResponseEntity<String> upload=http.postForEntity("/api/applications/"+app+"/materials",new HttpEntity<>(parts,headers),String.class);
        check(upload,201);
        return tree(upload).path("data").path("materialId").asLong();
    }
}
