package com.wjx871.population.dataops;

import static org.assertj.core.api.Assertions.*;
import com.wjx871.population.cache.OptionalRedisService;
import com.wjx871.population.security.*;
import java.time.Instant;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {
    "population.datasource.replica.enabled=true",
    "population.datasource.replica.url=jdbc:h2:mem:ops_replica;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "population.datasource.replica.username=sa", "population.datasource.replica.password=",
    "population.datasource.replica.driver-class-name=org.h2.Driver", "population.scheduling.enabled=false"
})
@Import(ReadWriteRoutingIntegrationTest.Probes.class)
class ReadWriteRoutingIntegrationTest {
    @Autowired @Qualifier("primaryDataSource") DataSource primary;
    @Autowired @Qualifier("replicaDataSource") DataSource replica;
    @Autowired Probe probe;
    @Autowired Writer writer;
    @Autowired JdbcTokenRevocationStore store;
    @Autowired OptionalRedisService redis;

    @BeforeEach void markers() {
        for (DataSource source : new DataSource[]{primary, replica}) {
            var jdbc = new JdbcTemplate(source);
            jdbc.execute("CREATE TABLE IF NOT EXISTS ops_route_probe(id INT PRIMARY KEY, label VARCHAR(32))");
            jdbc.update("DELETE FROM ops_route_probe");
            jdbc.update("INSERT INTO ops_route_probe VALUES (1, ?)", source == primary ? "PRIMARY" : "REPLICA");
        }
    }
    @Test void explicitReadUsesReplicaButPlainAndReadOnlyTransactionsStayPrimary() {
        assertThat(probe.lagTolerant()).isEqualTo("REPLICA");
        assertThat(probe.strongRead()).isEqualTo("PRIMARY");
        assertThat(probe.plain()).isEqualTo("PRIMARY");
    }
    @Test void writeTransactionPinsNestedReplicaReadToPrimary() {
        assertThat(writer.writeThenRead()).isEqualTo("WRITTEN");
        assertThat(probe.lagTolerant()).isEqualTo("REPLICA");
    }
    @Test void failedCallCleansRoutingContext() {
        assertThatThrownBy(() -> probe.fail()).isInstanceOf(IllegalStateException.class);
        assertThat(probe.plain()).isEqualTo("PRIMARY");
    }
    @Test void separateInstancesAndRestartShareDurableRevocationWithoutRedis() {
        String jti = java.util.UUID.randomUUID().toString();
        var instanceA = new TokenRevocationService(redis, store);
        var instanceB = new TokenRevocationService(redis, new JdbcTokenRevocationStore(primary));
        assertThat(instanceB.isRevoked(jti)).isFalse();
        instanceA.revoke(jti, Instant.now().plusSeconds(60));
        instanceA.revoke(jti, Instant.now().plusSeconds(60));
        assertThat(instanceB.isRevoked(jti)).isTrue();
        assertThat(new TokenRevocationService(redis, new JdbcTokenRevocationStore(primary)).isRevoked(jti)).isTrue();
        // Replica has no security_token_revocation table: this must still query the physical primary.
        assertThat(probe.revoked(jti)).isTrue();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Probes {
        @Bean Probe probe(DataSource source, JdbcTokenRevocationStore store) { return new Probe(new JdbcTemplate(source), store); }
        @Bean Writer writer(DataSource source, Probe probe) { return new Writer(new JdbcTemplate(source), probe); }
    }
    public static class Probe {
        private final JdbcTemplate jdbc;
        private final JdbcTokenRevocationStore store;
        Probe(JdbcTemplate jdbc, JdbcTokenRevocationStore store) { this.jdbc = jdbc; this.store = store; }
        @ReplicaRead @Transactional(readOnly = true) public String lagTolerant() { return plain(); }
        @Transactional(readOnly = true) public String strongRead() { return plain(); }
        public String plain() { return jdbc.queryForObject("SELECT label FROM ops_route_probe WHERE id=1", String.class); }
        @ReplicaRead public String fail() { throw new IllegalStateException("expected"); }
        @ReplicaRead public boolean revoked(String jti) { return store.isRevoked(jti); }
    }
    public static class Writer {
        private final JdbcTemplate jdbc;
        private final Probe probe;
        Writer(JdbcTemplate jdbc, Probe probe) { this.jdbc = jdbc; this.probe = probe; }
        @Transactional public String writeThenRead() {
            jdbc.update("UPDATE ops_route_probe SET label='WRITTEN' WHERE id=1");
            return probe.lagTolerant();
        }
    }
}
