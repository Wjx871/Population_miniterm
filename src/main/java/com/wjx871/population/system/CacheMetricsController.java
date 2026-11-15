package com.wjx871.population.system;

import com.wjx871.population.cache.OptionalRedisService;
import com.wjx871.population.common.ApiResponse;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CacheMetricsController {
    private final OptionalRedisService redis;
    private final String instanceId;

    public CacheMetricsController(OptionalRedisService redis,
                                  @Value("${population.instance-id:local}") String instanceId) {
        this.redis = redis;
        this.instanceId = instanceId;
    }

    @GetMapping("/api/ops/cache")
    @PreAuthorize("hasAuthority('system:user:manage')")
    public ApiResponse<Map<String, Object>> metrics() {
        return ApiResponse.ok(Map.of("instanceId", instanceId, "enabled", redis.enabled(),
                "scope", "process_since_start", "counters", redis.snapshot()));
    }
}
