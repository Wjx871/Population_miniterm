package com.wjx871.population.reference;

import static org.assertj.core.api.Assertions.*;

import com.wjx871.population.auth.AuthService;
import com.wjx871.population.support.ControllableRedisTestConfiguration;
import com.wjx871.population.support.ControllableRedisTestConfiguration.Store;
import java.util.*;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.*;

@SpringBootTest(properties = "population.redis.enabled=true")
@Import({ControllableRedisTestConfiguration.class, ReferenceCacheTransactionIntegrationTest.ProbeConfig.class})
class ReferenceCacheTransactionIntegrationTest {
    @Autowired DataDictionaryService dictionaries;
    @Autowired AdminRegionService regions;
    @Autowired AuthService auth;
    @Autowired JdbcTemplate jdbc;
    @Autowired Store store;
    @Autowired PlatformTransactionManager manager;
    final MockHttpServletRequest request = new MockHttpServletRequest();

    @TestConfiguration static class ProbeConfig {
        @Bean MapperProbe referenceMapperProbe(Store store) { return new MapperProbe(store); }
    }
    @Aspect static class MapperProbe {
        private final Store store;
        MapperProbe(Store store) { this.store = store; }
        @Before("execution(* com.wjx871.population.reference.DataDictionaryMapper.*(..)) || execution(* com.wjx871.population.reference.AdminRegionMapper.*(..))")
        public void observe(JoinPoint point) { store.observe("DB:" + point.getSignature().getName()); }
    }

    @BeforeEach void setup() {
        var user = auth.loadUser("admin");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
        jdbc.update("DELETE FROM data_dictionary WHERE dict_type='S5A2_TX'");
        jdbc.update("INSERT INTO data_dictionary(dict_type,dict_code,dict_name,sort_no,status,version) VALUES('S5A2_TX','ON','before',1,'ENABLED',0)");
        jdbc.update("DELETE FROM admin_region WHERE region_code='990001'");
        store.reset();
    }
    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); store.reset(); }
    TransactionTemplate tx() { return new TransactionTemplate(manager); }
    long id() { return jdbc.queryForObject("SELECT dict_id FROM data_dictionary WHERE dict_type='S5A2_TX'", Long.class); }
    void warm() { dictionaries.byType("S5A2_TX"); regions.tree(); regions.children("110100"); }
    String dictionaryName() { return dictionaries.byType("S5A2_TX").get(0).getDictName(); }
    void assertNoRedisSince(int count) {
        assertThat(store.observations.subList(count, store.observations.size()))
                .allMatch(o -> o.operation().startsWith("DB:"));
    }

    @Test void writeTransactionReadsItsWritesWithoutRedisAndCommitEvicts() {
        warm(); int observations = store.observations.size();
        tx().executeWithoutResult(status -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            assertThat(TransactionSynchronizationManager.isCurrentTransactionReadOnly()).isFalse();
            dictionaries.update(id(), new DictionaryUpdateRequest("after", 1, 0), request);
            assertThat(dictionaryName()).isEqualTo("after");
            assertNoRedisSince(observations);
            assertThat(store.evictions.get()).isZero();
        });
        assertThat(store.evictions.get()).isEqualTo(1);
        assertThat(dictionaryName()).isEqualTo("after");
        assertThat(store.observations).anyMatch(o -> o.operation().equals("DB:update") && o.active() && !o.readOnly());
    }
    @Test void rollbackKeepsCacheAndRestoresDatabase() {
        warm(); Map<String,String> before = Map.copyOf(store.values);
        tx().executeWithoutResult(status -> {
            dictionaries.update(id(), new DictionaryUpdateRequest("rolled back", 1, 0), request);
            assertThat(dictionaryName()).isEqualTo("rolled back");
            status.setRollbackOnly();
        });
        assertThat(store.evictions.get()).isZero();
        assertThat(store.values).containsExactlyInAnyOrderEntriesOf(before);
        assertThat(jdbc.queryForObject("SELECT dict_name FROM data_dictionary WHERE dict_id=?", String.class, id())).isEqualTo("before");
    }
    @Test void coldWriteTransactionCannotPublishUncommittedValueOnRollback() {
        tx().executeWithoutResult(status -> {
            dictionaries.update(id(), new DictionaryUpdateRequest("uncommitted", 1, 0), request);
            assertThat(dictionaryName()).isEqualTo("uncommitted");
            assertThat(store.gets.get()).isZero(); assertThat(store.puts.get()).isZero();
            status.setRollbackOnly();
        });
        assertThat(store.values).isEmpty();
        assertThat(dictionaryName()).isEqualTo("before");
    }
    @Test void regionWriteTransactionBypassesTreeAndChildrenCache() {
        warm(); int observations = store.observations.size();
        tx().executeWithoutResult(status -> {
            regions.create(new AdminRegionCreateRequest("990001", "new root", null, 1, "new root", 1), request);
            assertThat(regions.tree()).extracting(AdminRegion::getRegionCode).contains("990001");
            assertThat(regions.children("110100")).isNotEmpty();
            assertNoRedisSince(observations);
            status.setRollbackOnly();
        });
        assertThat(store.evictions.get()).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM admin_region WHERE region_code='990001'", Integer.class)).isZero();
        assertThat(regions.tree()).extracting(AdminRegion::getRegionCode).doesNotContain("990001");
    }
    @Test void independentServiceCallCommitsBeforeInvalidationAdvice() {
        warm(); store.observations.clear();
        dictionaries.update(id(), new DictionaryUpdateRequest("committed", 1, 0), request);
        assertThat(store.observations).anyMatch(o -> o.operation().equals("DB:update") && o.active() && !o.readOnly());
        assertThat(store.observations).filteredOn(o -> o.operation().equals("KEYS") || o.operation().equals("DELETE"))
                .isNotEmpty().allMatch(o -> !o.active());
        assertThat(jdbc.queryForObject("SELECT dict_name FROM data_dictionary WHERE dict_id=?", String.class, id())).isEqualTo("committed");
    }
    @Test void readOnlyTransactionMayUseCache() {
        TransactionTemplate read = tx(); read.setReadOnly(true);
        read.executeWithoutResult(status -> { assertThat(dictionaryName()).isEqualTo("before"); assertThat(dictionaryName()).isEqualTo("before"); });
        assertThat(store.puts.get()).isEqualTo(1);
        assertThat(store.observations).filteredOn(o -> o.operation().equals("GET"))
                .hasSize(2).allMatch(o -> o.active() && o.readOnly());
    }
    @Test void dictionaryCreateUpdateDisableEnableEachInvalidateAfterCommit() {
        warm();
        DataDictionary created = dictionaries.create(new DictionaryCreateRequest("S5A2_TX", "NEW", "new", 2), request);
        assertThat(store.evictions.get()).isEqualTo(1);
        dictionaries.byType("S5A2_TX");
        dictionaries.update(created.getDictId(), new DictionaryUpdateRequest("changed", 2, 0), request);
        assertThat(store.evictions.get()).isEqualTo(2);
        dictionaries.byType("S5A2_TX"); dictionaries.disable(created.getDictId(), new VersionRequest(1), request);
        assertThat(store.evictions.get()).isEqualTo(3);
        dictionaries.byType("S5A2_TX"); dictionaries.enable(created.getDictId(), new VersionRequest(2), request);
        assertThat(store.evictions.get()).isEqualTo(4);
        assertThat(dictionaries.byType("S5A2_TX")).extracting(DataDictionary::getDictName).contains("changed");
    }
    @Test void regionCreateUpdateDisableEnableEachInvalidateAfterCommit() {
        warm(); regions.create(new AdminRegionCreateRequest("990001", "new", null, 1, "new", 1), request);
        assertThat(store.evictions.get()).isEqualTo(1);
        regions.tree(); regions.update("990001", new AdminRegionUpdateRequest("changed", null, 1, "changed", 1, 0), request);
        assertThat(store.evictions.get()).isEqualTo(2);
        regions.tree(); regions.disable("990001", new VersionRequest(1), request);
        assertThat(store.evictions.get()).isEqualTo(3);
        regions.tree(); regions.enable("990001", new VersionRequest(2), request);
        assertThat(store.evictions.get()).isEqualTo(4);
        assertThat(regions.tree()).extracting(AdminRegion::getRegionCode).contains("990001");
    }
    @Test void failedOptimisticUpdateDoesNotInvalidate() {
        warm(); Map<String,String> before = Map.copyOf(store.values);
        assertThatThrownBy(() -> dictionaries.update(id(), new DictionaryUpdateRequest("bad", 1, 99), request)).isInstanceOf(RuntimeException.class);
        assertThat(store.evictions.get()).isZero(); assertThat(store.values).isEqualTo(before);
        assertThat(dictionaryName()).isEqualTo("before");
    }
    @Test void evictionKeysFailureDoesNotBreakSuccessfulCommit() { evictionFailure(true); }
    @Test void evictionDeleteFailureDoesNotBreakSuccessfulCommit() { evictionFailure(false); }
    private void evictionFailure(boolean keys) {
        warm(); store.failKeys = keys; store.failDelete = !keys;
        assertThatCode(() -> dictionaries.update(id(), new DictionaryUpdateRequest("db committed", 1, 0), request)).doesNotThrowAnyException();
        assertThat(jdbc.queryForObject("SELECT dict_name FROM data_dictionary WHERE dict_id=?", String.class, id())).isEqualTo("db committed");
        store.failGet = true;
        assertThat(dictionaryName()).isEqualTo("db committed");
    }
}
