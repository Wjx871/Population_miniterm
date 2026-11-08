package com.wjx871.population.reference;

import static org.assertj.core.api.Assertions.assertThat;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;

/** Real TCP HTTP, JWT/RBAC, transactions and JDBC; no shared or production fixtures. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"spring.datasource.hikari.maximum-pool-size=12", "server.address=127.0.0.1"})
abstract class Issue8183HttpSupport {
    @Autowired TestRestTemplate http;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    final Map<String, String> tokens = new HashMap<>();
    String prefix;

    @BeforeEach void isolatedIdentityAndLogin() throws Exception {
        try (var c = jdbc.getDataSource().getConnection()) { verifyDatabase(c); }
        prefix = "h" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        for (String user : List.of("admin", "viewer")) {
            var r = http.postForEntity("/api/auth/login", Map.of("username", user, "password", "123456"), String.class);
            check(r, 200);
            tokens.put(user, data(r).path("token").asText());
        }
    }
    void verifyDatabase(java.sql.Connection c) throws Exception {
        assertThat(c.getMetaData().getURL()).startsWith("jdbc:h2:mem:");
    }
    ResponseEntity<String> request(String method, String codeOrPath, String user, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokens.get(user));
        headers.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange("/api/admin-regions" + codeOrPath, HttpMethod.valueOf(method),
            new HttpEntity<>(body, headers), String.class);
    }
    ResponseEntity<String> create(String suffix, String parent, int level) {
        Map<String, Object> q = fields(parent, level);
        q.put("regionCode", prefix + suffix);
        return request("POST", "", "admin", q);
    }
    Map<String, Object> fields(String parent, int level) {
        Map<String, Object> q = new LinkedHashMap<>();
        q.put("regionName", "synthetic hierarchy"); q.put("fullName", "Issue81/83 synthetic only");
        q.put("parentCode", parent); q.put("regionLevel", level); q.put("sortNo", 71);
        return q;
    }
    ResponseEntity<String> move(String code, String parent, int level, int version) {
        var q = fields(parent, level); q.put("version", version);
        return request("PUT", "/" + code, "admin", q);
    }
    ResponseEntity<String> state(String code, String action, int version) {
        return request("POST", "/" + code + "/" + action, "admin", Map.of("version", version));
    }
    String node(String suffix, String parent, int level) {
        check(create(suffix, parent, level), 201); return prefix + suffix;
    }
    long id(String code) { return jdbc.queryForObject("SELECT region_id FROM admin_region WHERE region_code=?", Long.class, code); }
    void historicalStatus(String code, String status) {
        jdbc.update("UPDATE admin_region SET status=? WHERE region_code=?", status, code);
    }
    List<Map<String, Object>> snapshot() {
        return jdbc.queryForList("SELECT * FROM admin_region WHERE region_code LIKE ? ORDER BY region_id", prefix + "%");
    }
    List<Map<String, Object>> states() {
        return jdbc.queryForList("SELECT region_id,region_code,parent_id,status,version FROM admin_region WHERE region_code LIKE ? ORDER BY region_id", prefix + "%");
    }
    int audits() { return jdbc.queryForObject("SELECT COUNT(*) FROM operation_log WHERE operation_type LIKE 'REGION_%'", Integer.class); }
    JsonNode data(ResponseEntity<String> r) throws Exception { return json.readTree(r.getBody()).path("data"); }
    void check(ResponseEntity<String> r, int expected) {
        assertThat(r.getStatusCode().value()).as(r.getBody()).isEqualTo(expected);
    }
    void evidence(String scenario, ResponseEntity<String> r) {
        System.out.println("ISSUE8183 " + scenario + " HTTP=" + r.getStatusCode().value() + " response=" + r.getBody() + " rows=" + snapshot());
    }
}
