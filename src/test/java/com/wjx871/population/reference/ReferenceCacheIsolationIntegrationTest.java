package com.wjx871.population.reference;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wjx871.population.auth.AuthService;
import com.wjx871.population.security.*;
import com.wjx871.population.support.ControllableRedisTestConfiguration;
import com.wjx871.population.support.ControllableRedisTestConfiguration.Store;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "population.redis.enabled=true")
@AutoConfigureMockMvc
@Import(ControllableRedisTestConfiguration.class)
class ReferenceCacheIsolationIntegrationTest {
    @Autowired DataDictionaryService service;
    @Autowired Store store;
    @Autowired JdbcTemplate jdbc;
    @Autowired AuthService auth;
    @Autowired MockMvc mvc;

    @BeforeEach void setup() {
        store.reset();
        jdbc.update("DELETE FROM data_dictionary WHERE dict_type IN ('S5A2_TYPE','S5A2_OTHER')");
        jdbc.update("INSERT INTO data_dictionary(dict_type,dict_code,dict_name,sort_no,status,version) VALUES('S5A2_TYPE','ON','enabled',1,'ENABLED',0),('S5A2_TYPE','OFF','disabled',2,'DISABLED',0),('S5A2_OTHER','OTHER','other',1,'ENABLED',0)");
        assertThat(AopUtils.isAopProxy(service)).isTrue();
    }
    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); store.reset(); }

    @Test void administratorThenViewerNeverLeaksDisabled() {
        login(1, true); assertThat(codes()).containsExactly("ON", "OFF");
        login(2, false); assertThat(codes()).containsExactly("ON");
    }
    @Test void viewerThenAdministratorKeepsCompleteResults() {
        login(2, false); assertThat(codes()).containsExactly("ON");
        login(1, true); assertThat(codes()).containsExactly("ON", "OFF");
    }
    @Test void sameUserPermissionDowngradeSelectsNewPartition() {
        login(1, true); codes();
        login(1, false); assertThat(codes()).containsExactly("ON");
        login(1, true); assertThat(codes()).containsExactly("ON", "OFF");
        assertThat(store.puts.get()).isEqualTo(2);
    }
    @Test void administratorsShareCacheByPermissionRatherThanRoleOrIdentity() {
        login(1, true); codes();
        jdbc.update("UPDATE data_dictionary SET dict_name='database changed' WHERE dict_type='S5A2_TYPE'");
        login(2, true);
        assertThat(service.byType("S5A2_TYPE")).extracting(DataDictionary::getDictName)
                .containsExactly("enabled", "disabled");
        assertThat(store.puts.get()).isEqualTo(1);
        assertThat(store.gets.get()).isEqualTo(2);
    }
    @Test void viewersShareOnlyEnabledResults() {
        login(1, false); codes(); login(2, false);
        assertThat(codes()).containsExactly("ON");
        assertThat(store.puts.get()).isEqualTo(1);
    }
    @Test void typeNormalizationUsesRootLocaleAndSeparatesTypes() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            login(1, false); assertThat(service.byType(" certificate_type ")).hasSize(3);
            assertThat(service.byType("CERTIFICATE_TYPE")).hasSize(3);
            assertThat(service.byType("s5a2_other")).extracting(DataDictionary::getDictCode).containsExactly("OTHER");
            assertThat(store.puts.get()).isEqualTo(2);
        } finally { Locale.setDefault(previous); }
    }
    @Test void noAuthenticationCannotReadWarmCache() {
        login(1, true); codes();
        login(2, false); codes();
        int reads = store.gets.get();
        SecurityContextHolder.clearContext();
        assertThatThrownBy(this::codes).isInstanceOf(IllegalStateException.class);
        assertThat(store.gets.get()).isEqualTo(reads);
    }
    @Test void unauthenticatedTokenCannotReadWarmCache() {
        login(1, false); codes();
        var user = CurrentUserContext.requireUser();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null));
        int reads = store.gets.get();
        assertThatThrownBy(this::codes).isInstanceOf(IllegalStateException.class);
        assertThat(store.gets.get()).isEqualTo(reads);
    }
    @Test void httpEndpointStillRejectsUnauthenticatedRequestWithWarmCache() throws Exception {
        login(1, false); codes(); SecurityContextHolder.clearContext();
        mvc.perform(get("/api/dictionaries/S5A2_TYPE")).andExpect(status().isUnauthorized());
    }
    @Test void redisGetAndSetFailuresReturnAuthorizedDatabaseResults() {
        store.failGet = true; store.failSet = true;
        login(1, true); assertThat(codes()).containsExactly("ON", "OFF");
        login(2, false); assertThat(codes()).containsExactly("ON");
        assertThat(store.gets.get()).isEqualTo(2);
        assertThat(store.puts.get()).isEqualTo(2);
        assertThat(store.values).isEmpty();
    }
    @Test void changingCachedReturnObjectDoesNotMutateDatabaseOrStoredJson() {
        login(1, false);
        service.byType("S5A2_TYPE").get(0).setStatus("DISABLED");
        assertThat(service.byType("S5A2_TYPE")).extracting(DataDictionary::getStatus).containsExactly("ENABLED");
        assertThat(jdbc.queryForObject("SELECT status FROM data_dictionary WHERE dict_type='S5A2_TYPE' AND dict_code='ON'", String.class)).isEqualTo("ENABLED");
        assertThat(store.puts.get()).isEqualTo(1);
    }
    @Test void legacySharedKeyCannotContaminateNewNamespace() {
        store.values.put("population:dictionary:S5A2_TYPE", "[{\"dictCode\":\"OFF\",\"status\":\"DISABLED\"}]");
        login(1, false); assertThat(codes()).containsExactly("ON");
        assertThat(store.values.keySet()).anyMatch(k -> k.startsWith("population:dictionary:v2:ENABLED_ONLY:"));
    }
    @Test void concurrentUsersNeverMixPermissionPartitions() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Callable<List<String>>> calls = new ArrayList<>();
            for (int i = 0; i < 24; i++) {
                final boolean manage = i % 2 == 0;
                calls.add(() -> { try { login(manage ? 1 : 2, manage); return codes(); }
                    finally { SecurityContextHolder.clearContext(); } });
            }
            var results = pool.invokeAll(calls);
            for (int i = 0; i < results.size(); i++)
                assertThat(results.get(i).get()).containsExactlyElementsOf(i % 2 == 0 ? List.of("ON", "OFF") : List.of("ON"));
        } finally { pool.shutdownNow(); }
    }
    @Test void ordinaryQueriesWithoutTransactionUseCache() {
        login(1, false); codes(); codes();
        assertThat(store.puts.get()).isEqualTo(1);
        assertThat(store.observations).allMatch(o -> !o.active());
    }
    @Test void unspecifiedTypesKeepOriginalQuerySemanticsWithoutCaching() {
        login(1, false);
        assertThat(service.byType(null)).isNotEmpty().allMatch(v -> "ENABLED".equals(v.getStatus()));
        assertThat(service.byType(" ")).isNotEmpty().allMatch(v -> "ENABLED".equals(v.getStatus()));
        assertThat(store.gets.get()).isZero();
        assertThat(store.puts.get()).isZero();
    }

    private List<String> codes() { return service.byType("S5A2_TYPE").stream().map(DataDictionary::getDictCode).toList(); }
    private void login(long id, boolean manage) {
        // Deliberately identical role labels: effective permissions alone select the partition.
        AuthenticatedUser u = new AuthenticatedUser(id, "synthetic-" + id, "", "test", "ENABLED", 1L,
                "SAME_ROLE", "same role", null, DataScope.ALL, "ENABLED", 1L, "test", "110105",
                manage ? List.of("dictionary:view", "dictionary:manage") : List.of("dictionary:view"));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(u, null, u.getAuthorities()));
    }
}
