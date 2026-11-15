package com.wjx871.population.system;

import com.wjx871.population.common.ApiResponse;
import com.zaxxer.hikari.HikariDataSource;
import java.lang.management.ManagementFactory;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
public class RuntimeMetricsController {
    private final RequestMetrics requests;
    private final HikariDataSource primary;
    private final ObjectProvider<HikariDataSource> replica;
    private final String instanceId;
    public RuntimeMetricsController(RequestMetrics requests,
            @Qualifier("primaryDataSource") HikariDataSource primary,
            @Qualifier("replicaDataSource") ObjectProvider<HikariDataSource> replica,
            @Value("${population.instance-id:local}") String instanceId) {
        this.requests = requests; this.primary = primary; this.replica = replica; this.instanceId = instanceId;
    }
    @GetMapping("/api/ops/runtime")
    @PreAuthorize("hasAuthority('system:user:manage')")
    public ApiResponse<Map<String, Object>> metrics() {
        var result = new LinkedHashMap<String, Object>();
        result.put("instanceId", instanceId);
        result.put("sampledAt", Instant.now().toString());
        result.put("uptimeMs", ManagementFactory.getRuntimeMXBean().getUptime());
        result.put("requests", requests.snapshot());
        var memory = ManagementFactory.getMemoryMXBean();
        result.put("heapUsedBytes", memory.getHeapMemoryUsage().getUsed());
        result.put("heapCommittedBytes", memory.getHeapMemoryUsage().getCommitted());
        result.put("nonHeapUsedBytes", memory.getNonHeapMemoryUsage().getUsed());
        result.put("liveThreads", ManagementFactory.getThreadMXBean().getThreadCount());
        if (ManagementFactory.getOperatingSystemMXBean() instanceof com.sun.management.OperatingSystemMXBean os) {
            // JDK returns negative values when a load sample is not available; omit those fields.
            double process = os.getProcessCpuLoad(), system = os.getCpuLoad();
            if (process >= 0) result.put("processCpuRatio", process);
            if (system >= 0) result.put("systemCpuRatio", system);
        }
        var pools = new LinkedHashMap<String, Object>();
        pools.put("primary", pool(primary));
        HikariDataSource replicaPool = replica.getIfAvailable();
        if (replicaPool != null) pools.put("replica", pool(replicaPool));
        result.put("connectionPools", pools);
        return ApiResponse.ok(result);
    }
    private Map<String, Object> pool(HikariDataSource source) {
        var state = source.getHikariPoolMXBean();
        if (state == null) return Map.of("started", false);
        return Map.of("started", true, "active", state.getActiveConnections(), "idle", state.getIdleConnections(),
                "total", state.getTotalConnections(), "waitingThreads", state.getThreadsAwaitingConnection(),
                "maximum", source.getMaximumPoolSize());
    }
}
