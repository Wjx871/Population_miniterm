package com.wjx871.population.system;

import com.wjx871.population.common.ApiResponse;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController @RequiredArgsConstructor
public class RabbitMqMetricsController {
    private final RabbitMqMetricsService metrics;
    @GetMapping("/api/ops/rabbitmq")
    @PreAuthorize("hasAuthority('system:user:manage')")
    public ApiResponse<Map<String, Object>> metrics() { return ApiResponse.ok(metrics.snapshot()); }
}
