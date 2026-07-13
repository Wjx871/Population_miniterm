package com.wjx871.population.system;

import com.wjx871.population.cache.OptionalRedisService;
import com.wjx871.population.common.ApiResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/health")
@RequiredArgsConstructor
public class HealthController {
    private final DataSource dataSource;
    private final OptionalRedisService redis;

    @GetMapping
    public ApiResponse<Map<String, Object>> health() {
        Map<String, Object> result = new LinkedHashMap<>();
        boolean database;
        try { database = Integer.valueOf(1).equals(new JdbcTemplate(dataSource).queryForObject("SELECT 1", Integer.class)); }
        catch (RuntimeException ex) { database = false; }
        boolean redisUp = redis.enabled() && redis.ping();
        result.put("database", database ? "UP" : "DOWN");
        result.put("redisEnabled", redis.enabled());
        result.put("redisStatus", !redis.enabled() ? "DISABLED" : redisUp ? "UP" : "DOWN");
        result.put("cacheMode", redisUp ? "REDIS" : "MYSQL_FALLBACK");
        return ApiResponse.ok(result);
    }
}
