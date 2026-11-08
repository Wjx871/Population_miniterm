package com.wjx871.population.query;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Issue #68/#71 的 MySQL 8.4 专用门禁。
 *
 * <p>本类名称以 {@code IT} 结尾，普通 {@code mvn test} 不会连接外部数据库；只有
 * {@code issue6871-mysql84-gate.ps1} 在自建 127.0.0.1 非 3306 实例、专属 schema、
 * 非 root 应用账号上显式执行它。</p>
 */
@SpringBootTest(classes = com.wjx871.population.PopulationMinitermApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.hikari.maximum-pool-size=8",
                "population.redis.enabled=false",
                "policy.ai.enabled=false"
        })
class Issue6871MySqlIT extends Issue6871ComprehensiveQueryIntegrationTest {

    @Autowired
    JdbcTemplate mysqlJdbc;

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry properties) {
        Issue6871MySqlGate.configure(properties);
    }

    @BeforeEach
    void verifyDedicatedMysqlIdentity() throws Exception {
        try (Connection connection = mysqlJdbc.getDataSource().getConnection()) {
            Issue6871MySqlGate.verify(connection);
        }
        assertThat(mysqlJdbc.queryForObject("SELECT @@transaction_isolation", String.class))
                .isEqualTo("REPEATABLE-READ");
    }

    @Test
    void mysqlUsesRealMysqlDialectAndRejectsFullGroupByViolations() {
        // The inherited contract tests execute the real XML SQL; this assertion records that
        // MySQL's ONLY_FULL_GROUP_BY remains enabled rather than being silently relaxed.
        assertThat(mysqlJdbc.queryForObject("SELECT @@sql_mode", String.class))
                .contains("ONLY_FULL_GROUP_BY");
        System.out.println("ISSUE6871 MYSQL sql_mode="
                + mysqlJdbc.queryForObject("SELECT @@sql_mode", String.class));
    }
}
