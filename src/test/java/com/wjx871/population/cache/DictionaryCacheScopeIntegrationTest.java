package com.wjx871.population.cache;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "population.redis.enabled=true")
@AutoConfigureMockMvc
class DictionaryCacheScopeIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @MockitoBean StringRedisTemplate redis;
    private final Map<String, String> values = new ConcurrentHashMap<>();

    @BeforeEach @SuppressWarnings("unchecked") void fakeRedis() {
        values.clear();
        ValueOperations<String, String> operations = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(operations);
        when(operations.get(anyString())).thenAnswer(i -> values.get(i.getArgument(0)));
        doAnswer(i -> { values.put(i.getArgument(0), i.getArgument(1)); return null; })
                .when(operations).set(anyString(), anyString(), any(java.time.Duration.class));
        when(redis.scan(any(ScanOptions.class))).thenAnswer(i -> {
            var iterator = new java.util.ArrayList<>(values.keySet()).iterator();
            Cursor<String> cursor = mock(Cursor.class);
            when(cursor.hasNext()).thenAnswer(call -> iterator.hasNext());
            when(cursor.next()).thenAnswer(call -> iterator.next());
            return cursor;
        });
        when(redis.delete(anyCollection())).thenAnswer(i -> {
            long count = 0;
            for (String key : (java.util.Collection<String>) i.getArgument(0)) if (values.remove(key) != null) count++;
            return count;
        });
    }
    @Test void adminCacheCannotExposeDisabledDictionaryItemsToViewer() throws Exception {
        assertThat(read("admin")).contains("DISABLED_TEST");
        assertThat(read("viewer")).contains("PASSPORT").doesNotContain("DISABLED_TEST");
        assertThat(read("admin")).contains("DISABLED_TEST");
        assertThat(read("viewer")).doesNotContain("DISABLED_TEST");
    }
    @Test void viewerCacheCannotHideDisabledItemsFromAdministrator() throws Exception {
        assertThat(read("viewer")).doesNotContain("DISABLED_TEST");
        assertThat(read("admin")).contains("DISABLED_TEST");
    }
    @Test void dictionaryInvalidatesOnlyAfterCommitAndKeepsCacheOnRollback() throws Exception {
        String credentials = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"admin\",\"password\":\"123456\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String token = json.readTree(credentials).path("data").path("token").asText();
        String type = "COMMIT_" + java.util.UUID.randomUUID().toString().replace("-", "").toUpperCase();
        String created = mvc.perform(post("/api/dictionaries").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
                        "dictionaryType", type, "dictionaryCode", "ONE", "displayName", "original", "sortNo", 0))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = json.readTree(created).path("data").path("dictId").asLong();
        mvc.perform(get("/api/dictionaries/" + type).header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        String key = "population:dictionary:v2:MANAGE:" + type;
        String original = values.get(key);
        assertThat(original).contains("original");
        var template = new TransactionTemplate(transactions);
        for (boolean rollback : new boolean[]{true, false}) {
            template.executeWithoutResult(transaction -> {
                try {
                    mvc.perform(put("/api/dictionaries/" + id).header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"displayName\":\"changed\",\"sortNo\":0,\"version\":0}"))
                            .andExpect(status().isOk());
                } catch (Exception ex) { throw new RuntimeException(ex); }
                assertThat(jdbc.queryForObject("SELECT dict_name FROM data_dictionary WHERE dict_id=?", String.class, id)).isEqualTo("changed");
                assertThat(values.get(key)).isEqualTo(original);
                if (rollback) transaction.setRollbackOnly();
            });
            assertThat(jdbc.queryForObject("SELECT dict_name FROM data_dictionary WHERE dict_id=?", String.class, id))
                    .isEqualTo(rollback ? "original" : "changed");
            if (rollback) assertThat(values.get(key)).isEqualTo(original);
            else assertThat(values).doesNotContainKey(key);
        }
    }
    private String read(String user) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("username", user, "password", "123456"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String token = json.readTree(body).path("data").path("token").asText();
        return mvc.perform(get("/api/dictionaries/CERTIFICATE_TYPE").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
    }
}
