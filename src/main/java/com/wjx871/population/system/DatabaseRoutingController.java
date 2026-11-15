package com.wjx871.population.system;

import com.wjx871.population.common.ApiResponse;
import com.wjx871.population.dataops.ReadWriteDataSource;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class DatabaseRoutingController {
    private final ReadWriteDataSource routing;
    @GetMapping("/api/ops/database-routing")
    @PreAuthorize("hasAuthority('system:user:manage')")
    public ApiResponse<ReadWriteDataSource.RouteSnapshot> routes() { return ApiResponse.ok(routing.snapshot()); }
}
