package com.wjx871.population.stats;
import com.wjx871.population.common.ApiResponse;import com.wjx871.population.dashboard.*;import java.util.*;import lombok.RequiredArgsConstructor;import org.springframework.security.access.prepost.PreAuthorize;import org.springframework.web.bind.annotation.*;
@RestController@RequestMapping("/api/statistics")@RequiredArgsConstructor@PreAuthorize("hasAuthority('statistics:view')") public class UnifiedStatisticsController{private final UnifiedStatisticsService service;
 @GetMapping("/overview")public ApiResponse<DashboardOverviewView>overview(@RequestParam(defaultValue="30")int periodDays,@RequestParam(defaultValue="30")int expiryDays){return ApiResponse.ok(service.overview(periodDays,expiryDays));}
 @GetMapping("/population-trend")public ApiResponse<List<MigrationTrendPoint>>populationTrend(@RequestParam(defaultValue="30")int days){return ApiResponse.ok(service.migrationTrend(days));}
 @GetMapping("/region-distribution")public ApiResponse<List<RegionCountView>>region(@RequestParam(defaultValue="8")int limit){return ApiResponse.ok(service.regionDistribution(limit));}
 @GetMapping("/household-distribution")public ApiResponse<List<NamedCountView>>households(){return ApiResponse.ok(service.householdDistribution());}
 @GetMapping("/migration-trend")public ApiResponse<List<MigrationTrendPoint>>migration(@RequestParam(defaultValue="30")int days){return ApiResponse.ok(service.migrationTrend(days));}
 @GetMapping("/floating-population")public ApiResponse<Map<String,Object>>floating(){return ApiResponse.ok(service.floatingPopulation());}
 @GetMapping("/certificate-expiry")public ApiResponse<Map<String,Object>>expiry(@RequestParam(defaultValue="30")int days){return ApiResponse.ok(service.certificateExpiry(days));}
 @GetMapping("/key-population")public ApiResponse<Map<String,Object>>key(){return ApiResponse.ok(service.keyPopulation());}}
