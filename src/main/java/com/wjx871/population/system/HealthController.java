package com.wjx871.population.system;

import com.wjx871.population.cache.OptionalRedisService;
import com.wjx871.population.common.ApiResponse;
import com.wjx871.population.security.CheckpointSnapshot;
import com.wjx871.population.security.DurableRevocationStore;
import com.wjx871.population.security.RevocationProperties;
import com.wjx871.population.security.RevocationStoreUnavailableException;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/health")
@RequiredArgsConstructor
public class HealthController {
    private final DataSource dataSource;
    private final OptionalRedisService redis;
    private final RevocationProperties revocationProperties;
    private final ObjectProvider<DurableRevocationStore> durableStoreProvider;
    private final Environment environment;

    @GetMapping
    @PreAuthorize("permitAll()")
    public ApiResponse<Map<String, Object>> health() {
        Map<String, Object> result = new LinkedHashMap<>();
        boolean database;
        try { database = Integer.valueOf(1).equals(new JdbcTemplate(dataSource).queryForObject("SELECT 1", Integer.class)); }
        catch (RuntimeException ex) { database = false; }
        boolean redisUp = redis.enabled() && redis.ping();
        RevocationProperties.Mode mode = revocationProperties.getMode();
        result.put("database", database ? "UP" : "DOWN");
        result.put("redisEnabled", redis.enabled());
        result.put("redisStatus", !redis.enabled() ? "DISABLED" : redisUp ? "UP" : "DOWN");
        result.put("cacheMode", redisUp ? "REDIS" : "MYSQL_FALLBACK");
        result.put("revocationMode", mode != null ? mode.name() : "UNKNOWN");
        result.put("revocationStoreStatus", resolveRevocationStoreStatus(mode, database, redisUp));
        return ApiResponse.ok(result);
    }

    private String resolveRevocationStoreStatus(RevocationProperties.Mode mode, boolean database, boolean redisUp) {
        if (mode == null) {
            return "UNKNOWN";
        }
        return switch (mode) {
            case SINGLE_INSTANCE_LOCAL -> "LOCAL_MEMORY";
            case SHARED_REDIS_REQUIRED -> redisUp ? "REDIS_UP" : "REDIS_DOWN";
            case DURABLE_DB_REQUIRED -> resolveDurableStoreStatus(database);
        };
    }

    private String resolveDurableStoreStatus(boolean database) {
        if (!database) {
            return "DB_PRIMARY_DOWN";
        }
        DurableRevocationStore store = durableStoreProvider.getIfAvailable();
        if (store == null) {
            return "DB_PRIMARY_DOWN";
        }
        try {
            CheckpointSnapshot snapshot = store.probeAuthoritativeStore();
            boolean production = revocationProperties.isProduction()
                    || (environment != null && environment.acceptsProfiles(Profiles.of("prod", "production")));
            boolean zeroCutoffAllowed = !production && revocationProperties.isAllowZeroCutoffForTest();
            if (snapshot == null || !snapshot.isReady()
                    || (snapshot.minValidIatEpochMs() == 0L && !zeroCutoffAllowed)) {
                return "DB_CHECKPOINT_NOT_READY";
            }
            return "DB_PRIMARY_UP";
        } catch (RevocationStoreUnavailableException ex) {
            if (ex.getCause() instanceof IllegalStateException) {
                return "DB_CHECKPOINT_NOT_READY";
            }
            return "DB_REVOCATION_UNAVAILABLE";
        } catch (RuntimeException ex) {
            return "DB_REVOCATION_UNAVAILABLE";
        }
    }
}
