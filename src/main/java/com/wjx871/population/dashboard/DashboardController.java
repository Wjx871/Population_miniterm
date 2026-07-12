package com.wjx871.population.dashboard;

import com.wjx871.population.common.ApiResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {
    private final DashboardService service;

    @GetMapping("/overview")
    @PreAuthorize("hasAuthority('population:view')")
    public ApiResponse<DashboardOverviewView> overview(
            @RequestParam(defaultValue = "30") @Min(7) @Max(365) int periodDays,
            @RequestParam(defaultValue = "30") @Min(1) @Max(365) int expiryDays) {
        return ApiResponse.ok(service.overview(periodDays, expiryDays));
    }

    @GetMapping("/charts")
    @PreAuthorize("hasAuthority('population:view')")
    public ApiResponse<DashboardChartsView> charts(
            @RequestParam(defaultValue = "30") @Min(7) @Max(90) int days,
            @RequestParam(defaultValue = "8") @Min(1) @Max(20) int regionLimit) {
        return ApiResponse.ok(service.charts(days, regionLimit));
    }
}
