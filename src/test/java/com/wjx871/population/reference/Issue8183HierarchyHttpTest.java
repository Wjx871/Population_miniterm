package com.wjx871.population.reference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.wjx871.population.audit.OperationLogMapper;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Supplier;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.http.ResponseEntity;

class Issue8183HierarchyHttpTest extends Issue8183BaselineRedProbe {
    @SpyBean AdminRegionMapper mapper;
    @SpyBean OperationLogMapper audit;
    @Autowired SqlSessionTemplate sql;
    @AfterEach void resetProbes() { reset(mapper, audit); }

    void rejectedWithoutChanges(Supplier<ResponseEntity<String>> call, int status) {
        var rows = snapshot(); int logs = audits();
        var r = call.get(); evidence("rejection", r); check(r, status);
        assertThat(snapshot()).isEqualTo(rows); assertThat(audits()).isEqualTo(logs);
    }
    @Test void disabledHigherAncestorBlocksCreateAndMove() {
        String a=node("a",null,1), b=node("b",a,2), x=node("x",null,1), y=node("y",x,3);
        historicalStatus(a,"DISABLED");
        rejectedWithoutChanges(() -> create("c",b,3),409);
        rejectedWithoutChanges(() -> move(y,b,3,0),409);
    }
    @Test void enableChecksEntireAncestorChain() {
        String a=node("a",null,1), b=node("b",a,2), c=node("c",b,3);
        historicalStatus(a,"DISABLED"); historicalStatus(c,"DISABLED");
        rejectedWithoutChanges(() -> state(c,"enable",0),409);
        historicalStatus(a,"ENABLED"); check(state(c,"enable",0),200);
    }
    @Test void arbitraryDepthDisabledIntermediatesDoNotHideEnabledDescendant() {
        String a=node("a",null,1), b=node("b",a,2), c=node("c",b,3), d=node("d",c,4);
        node("e",d,5);
        for(String v:List.of(b,c,d))historicalStatus(v,"DISABLED");
        rejectedWithoutChanges(() -> state(a,"disable",0),409);
    }
    @Test void allDisabledDescendantsAndLeafAllowNormalTransitions() {
        String a=node("a",null,1), b=node("b",a,2), c=node("c",b,3);
        check(state(c,"disable",0),200); check(state(b,"disable",0),200); check(state(a,"disable",0),200);
        check(state(a,"enable",1),200); check(state(b,"enable",1),200); check(state(c,"enable",1),200);
        assertLegalHierarchy();
    }
    @Test void metadataReparentAndReadContractsStayCompatible() throws Exception {
        String a=node("a",null,1), b=node("b",a,2), c=node("c",b,3), x=node("x",null,1);
        var q=fields(b,3);q.put("version",0);q.put("regionName","renamed");q.put("sortNo",3);
        var updated=request("PUT","/"+c,"admin",q);check(updated,200);
        assertThat(data(updated).path("version").asInt()).isEqualTo(1);
        assertThat(data(updated).path("regionName").asText()).isEqualTo("renamed");
        check(move(c,x,3,1),200); // Existing contract allows skipped levels.
        for(String path:List.of("?keyword="+prefix,"/"+c,"/"+x+"/children","/tree")) {
            var r=request("GET",path,"viewer",null);check(r,200);assertThat(r.getBody()).contains(c);
        }
        check(state(c,"disable",2),200);
        assertThat(request("GET","/tree","viewer",null).getBody()).doesNotContain(c);
        assertThat(request("GET","/"+x+"/children","viewer",null).getBody()).contains(c).contains("DISABLED");
        assertThat(request("GET","/"+c,"viewer",null).getBody()).contains("DISABLED");
    }
    @Test void hiddenHistoricalEnabledChildIsNotPromotedByTree() {
        String a=node("a",null,1), b=node("b",a,2), c=node("c",b,3);
        historicalStatus(b,"DISABLED"); var before=snapshot();
        var r=request("GET","/tree","viewer",null);check(r,200);
        assertThat(r.getBody()).contains(a).doesNotContain(b).doesNotContain(c);
        assertThat(snapshot()).isEqualTo(before);
    }
    @Test void versionAndStateConflictsLeaveRowsAndAuditUnchanged() {
        String a=node("a",null,1), b=node("b",a,2);
        rejectedWithoutChanges(() -> move(b,a,2,99),409);
        rejectedWithoutChanges(() -> state(b,"disable",99),409);
        rejectedWithoutChanges(() -> state(b,"enable",0),409);
        check(state(b,"disable",0),200);
        rejectedWithoutChanges(() -> state(b,"disable",1),409);
        rejectedWithoutChanges(() -> state(b,"enable",0),409);
    }
    @Test void cycleAndRootRulesStayEnforced() {
        String a=node("a",null,1), b=node("b",a,2), c=node("c",b,3);
        rejectedWithoutChanges(() -> move(a,c,4,0),400);
        rejectedWithoutChanges(() -> move(b,b,3,0),400);
        rejectedWithoutChanges(() -> create("bad",null,2),400);
        rejectedWithoutChanges(() -> create("missing","absent",2),404);
        rejectedWithoutChanges(() -> create("level",a,1),400);
    }
    @Test void historicalCycleTerminatesAndIsNotAutomaticallyRepaired() {
        String a=node("a",null,1), b=node("b",a,2), c=node("c",b,3);
        jdbc.update("UPDATE admin_region SET parent_id=? WHERE region_code=?",id(c),a);
        rejectedWithoutChanges(() -> create("new",b,3),409);
        rejectedWithoutChanges(() -> state(b,"disable",0),409);
    }
    @Test void viewerCannotCallAnyManagementEndpoint() {
        String a=node("a",null,1);var q=fields(null,1);q.put("regionCode",prefix+"denied");
        rejectedWithoutChanges(() -> request("POST","","viewer",q),403);
        var update=fields(null,1);update.put("version",0);
        rejectedWithoutChanges(() -> request("PUT","/"+a,"viewer",update),403);
        for(String action:List.of("enable","disable"))
            rejectedWithoutChanges(() -> request("POST","/"+a+"/"+action,"viewer",Map.of("version",0)),403);
    }
    @Test void auditFailureRollsBackAlreadyExecutedInsert() {
        String a=node("a",null,1);
        doThrow(new IllegalStateException("synthetic transactional audit failure")).when(audit)
            .insert(argThat(v -> v != null && "REGION_CREATE".equals(v.getOperationType())));
        rejectedWithoutChanges(() -> create("b",a,2),500);
    }
    @Test void auditFailureRollsBackUpdateAndStateVersion() {
        String a=node("a",null,1),b=node("b",a,2);
        doThrow(new IllegalStateException("synthetic transactional audit failure")).when(audit)
            .insert(argThat(v -> v != null && v.getOperationType().startsWith("REGION_")));
        rejectedWithoutChanges(() -> move(b,a,2,0),500);
        rejectedWithoutChanges(() -> state(b,"disable",0),500);
    }
    @Test void createVersusDisableBothCommitOrdersFiveRounds() throws Exception {
        for(int i=0;i<5;i++) {
            String a=node("a"+i,null,1);
            race(() -> create("b"+id(a),a,2), () -> state(a,"disable",0), true,"create wins "+i,201,409);
            String x=node("x"+i,null,1);
            race(() -> state(x,"disable",0), () -> create("y"+id(x),x,2),false,"disable wins create "+i,200,409);
        }
    }
    @Test void moveVersusDisableBothCommitOrdersFiveRounds() throws Exception {
        for(int i=0;i<5;i++) {
            String a=node("a"+i,null,1),b=node("b"+i,null,1),c=node("c"+i,a,2);
            race(() -> move(c,b,2,0),() -> state(b,"disable",0),false,"move wins "+i,200,409);
            String x=node("x"+i,null,1),y=node("y"+i,a,2);
            race(() -> state(x,"disable",0),() -> move(y,x,2,0),false,"disable wins move "+i,200,409);
        }
    }
    @Test void enableVersusAncestorDisableBothCommitOrdersFiveRounds() throws Exception {
        for(int i=0;i<5;i++) {
            String a=node("a"+i,null,1),b=node("b"+i,a,2),c=node("c"+i,b,3);
            check(state(c,"disable",0),200);
            // Enabled B means A cannot disable regardless; compete on B with its disabled descendant C.
            race(() -> state(c,"enable",1),() -> state(b,"disable",0),false,"enable wins "+i,200,409);
            check(state(c,"disable",2),200);
            race(() -> state(b,"disable",0),() -> state(c,"enable",3),false,"disable wins enable "+i,200,409);
        }
    }
    @Test void simultaneousAdjacentMovesNeverCreateCycle() throws Exception {
        for(int i=0;i<5;i++) {
            String a=node("a"+i,null,1),b=node("b"+i,null,1);
            race(() -> move(a,b,2,0),() -> move(b,a,3,0),false,"adjacent moves "+i,200,409);
        }
    }
    @Test void movingParentWhileCreatingChildRejectsStalePath() throws Exception {
        String a=node("a",null,1),b=node("b",a,2),x=node("x",null,1);
        race(() -> move(b,x,2,0),() -> create("c",b,3),false,"changed ancestor path",200,409);
    }
    @Test void lockedOlderDescendantReturnsConflictWithoutWaitingOrChangingRows() throws Exception {
        String child=node("older",null,1),parent=node("later",null,1);
        check(move(child,parent,2,0),200);check(state(child,"disable",1),200);
        try(var c=jdbc.getDataSource().getConnection()) {
            c.setAutoCommit(false);
            try(var s=c.prepareStatement("SELECT region_id FROM admin_region WHERE region_id=? FOR UPDATE")) {
                s.setLong(1,id(child));try(var r=s.executeQuery()){assertThat(r.next()).isTrue();}
                rejectedWithoutChanges(() -> state(parent,"disable",0),409);
            } finally { c.rollback(); }
        }
        check(state(parent,"disable",0),200);
    }

    void race(Supplier<ResponseEntity<String>> first, Supplier<ResponseEntity<String>> second,
              boolean insert, String label, int firstExpected, int secondExpected) throws Exception {
        reset(mapper);
        var held=new CountDownLatch(1);var release=new CountDownLatch(1);var attempted=new CountDownLatch(1);
        var firstThread=new AtomicReference<Thread>();var armed=new AtomicBoolean(true);
        org.mockito.stubbing.Answer<Object> pause=inv -> {
            if(armed.compareAndSet(true,false)) {
                transactionEvidence();
                firstThread.set(Thread.currentThread());held.countDown();
                if(!release.await(10,TimeUnit.SECONDS))throw new AssertionError("fixture release timed out");
            }
            return realMapperCall(inv);
        };
        if(insert)doAnswer(pause).when(mapper).insert(any());
        else {doAnswer(pause).when(mapper).updateStatus(anyString(),anyString(),anyString(),anyInt());
            doAnswer(pause).when(mapper).update(any(),anyInt());}
        org.mockito.stubbing.Answer<Object> observe=inv -> {
            if(held.getCount()==0 && Thread.currentThread()!=firstThread.get())attempted.countDown();
            return realMapperCall(inv);
        };
        doAnswer(observe).when(mapper).findByCode(anyString());
        var pool=Executors.newFixedThreadPool(2);
        int before=audits();
        var beforeRows=snapshot();
        try {
            var f1=pool.submit(first::get);assertThat(held.await(10,TimeUnit.SECONDS)).isTrue();
            var f2=pool.submit(second::get);assertThat(attempted.await(10,TimeUnit.SECONDS)).isTrue();
            verifyWaiting(f2);release.countDown();
            var r1=f1.get(15,TimeUnit.SECONDS);var r2=f2.get(15,TimeUnit.SECONDS);
            check(r1,firstExpected);check(r2,secondExpected);assertLegalHierarchy();
            assertThat(audits()).isEqualTo(before+1);
            assertOneCommittedMutation(beforeRows,insert);
            System.out.println("ISSUE8183 RACE "+label+" HTTP="+r1.getStatusCode().value()+"/"+r2.getStatusCode().value()+" loser="+r2.getBody()+" rows="+states());
        } finally { release.countDown();pool.shutdown();assertThat(pool.awaitTermination(20,TimeUnit.SECONDS)).isTrue();reset(mapper); }
    }
    void assertOneCommittedMutation(List<Map<String,Object>> before,boolean inserted) {
        var after=snapshot();
        if(inserted) {
            assertThat(after).hasSize(before.size()+1).containsAll(before);
            var added=after.stream().filter(r -> before.stream().noneMatch(b -> b.get("region_id").equals(r.get("region_id")))).toList();
            assertThat(added).hasSize(1);assertThat(added.get(0).get("status")).isEqualTo("ENABLED");
            assertThat(((Number)added.get(0).get("version")).intValue()).isZero();
        } else {
            assertThat(after).hasSize(before.size());
            var changed=after.stream().filter(r -> !before.contains(r)).toList();
            assertThat(changed).hasSize(1);
            var row=changed.get(0);var old=before.stream().filter(b -> b.get("region_id").equals(row.get("region_id"))).findFirst().orElseThrow();
            assertThat(((Number)row.get("version")).intValue()).isEqualTo(((Number)old.get("version")).intValue()+1);
        }
    }
    Object realMapperCall(org.mockito.invocation.InvocationOnMock inv) throws Throwable {
        try { return inv.getMethod().invoke(sql.getMapper(AdminRegionMapper.class),inv.getArguments()); }
        catch(java.lang.reflect.InvocationTargetException e) { throw e.getCause(); }
    }
    void transactionEvidence() {
        assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
    }
    void verifyWaiting(Future<?> f) throws Exception {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(4);
        while(System.nanoTime()<deadline) {
            int blocked=jdbc.queryForObject("SELECT COUNT(*) FROM INFORMATION_SCHEMA.SESSIONS WHERE BLOCKER_ID IS NOT NULL",Integer.class);
            if(blocked>0){assertThat(f.isDone()).isFalse();System.out.println("ISSUE8183 H2 blockedSessions="+blocked);return;}
            Thread.sleep(15);
        }
        throw new AssertionError("No actual H2 row lock wait observed");
    }
    void assertLegalHierarchy() {
        for(var row:snapshot())if("ENABLED".equals(row.get("status"))) {
            Long parent=((Number)row.get("region_id")).longValue();Set<Long> seen=new HashSet<>();
            while(parent!=null) {
                assertThat(seen.add(parent)).as("no cycle").isTrue();
                var current=jdbc.queryForMap("SELECT parent_id,status FROM admin_region WHERE region_id=?",parent);
                assertThat(current.get("status")).isEqualTo("ENABLED");
                parent=current.get("parent_id")==null?null:((Number)current.get("parent_id")).longValue();
            }
        }
    }
}
