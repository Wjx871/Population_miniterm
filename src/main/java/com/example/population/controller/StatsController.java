package com.example.population.controller;

import com.example.population.annotation.RequiresPermission;
import com.example.population.dto.Result;
import com.example.population.service.StatsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 统计看板 / 数据大屏查询接口。
 *
 * <p>对应业务流程 §2.2.9。覆盖：表格列表、统计卡片、柱状图、饼图、折线图所需数据。
 * 所有接口均只返回汇总/计数，不返回人员/户口明细，避免敏感数据泄露。</p>
 *
 * <p><b>权限</b>：统一使用 {@code stats:query}；查询统计人员角色绑定该权限即可访问。</p>
 */
@Tag(name = "统计看板/大屏")
@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
public class StatsController {

    private final StatsService statsService;

    /** 综合数字卡（一次性返回所有汇总值）。 */
    @RequiresPermission("stats:query")
    @Operation(summary = "综合数字卡（人员/户口/户籍/流动/重点/证件/待审批）")
    @GetMapping("/dashboard")
    public Result<Map<String, Object>> dashboard() {
        return Result.success(statsService.dashboardCounters());
    }

    /** 人员档案状态。 */
    @RequiresPermission("stats:query")
    @Operation(summary = "人员档案状态分组（ACTIVE/CANCELLED）")
    @GetMapping("/person/status")
    public Result<List<Map<String, Object>>> personStatus(
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate) {
        return Result.success(statsService.countByPersonStatus(startDate, endDate));
    }

    /** 人员性别。 */
    @RequiresPermission("stats:query")
    @Operation(summary = "人员性别分组")
    @GetMapping("/person/gender")
    public Result<List<Map<String, Object>>> personGender() {
        return Result.success(statsService.countByPersonGender());
    }

    /** 民族 Top N。 */
    @RequiresPermission("stats:query")
    @Operation(summary = "人员民族分组 Top N")
    @GetMapping("/person/ethnicity")
    public Result<List<Map<String, Object>>> personEthnicity(
            @RequestParam(defaultValue = "10") int topN) {
        return Result.success(statsService.countByPersonEthnicity(topN));
    }

    /** 户口类型。 */
    @RequiresPermission("stats:query")
    @Operation(summary = "户口类型分组")
    @GetMapping("/household/type")
    public Result<List<Map<String, Object>>> householdType() {
        return Result.success(statsService.countByHouseholdType());
    }

    /** 户口状态。 */
    @RequiresPermission("stats:query")
    @Operation(summary = "户口状态分组")
    @GetMapping("/household/status")
    public Result<List<Map<String, Object>>> householdStatus() {
        return Result.success(statsService.countByHouseholdStatus());
    }

    /** 区划当前户籍人口数。 */
    @RequiresPermission("stats:query")
    @Operation(summary = "区划当前户籍人口数")
    @GetMapping("/population/by-region")
    public Result<List<Map<String, Object>>> populationByRegion() {
        return Result.success(statsService.populationByRegion());
    }

    /** 流动人口按区划。 */
    @RequiresPermission("stats:query")
    @Operation(summary = "流动人口按区划")
    @GetMapping("/floating/by-region")
    public Result<List<Map<String, Object>>> floatingByRegion(
            @RequestParam(required = false) String status) {
        return Result.success(statsService.floatingByRegion(status));
    }

    /** 证件状态。 */
    @RequiresPermission("stats:query")
    @Operation(summary = "证件状态分组")
    @GetMapping("/certificate/status")
    public Result<List<Map<String, Object>>> certificateStatus() {
        return Result.success(statsService.countByCertificateStatus());
    }

    /** 证件即将到期/已过期。 */
    @RequiresPermission("stats:query")
    @Operation(summary = "证件到期/过期分组（warnDays 默认为 30）")
    @GetMapping("/certificate/expiring")
    public Result<List<Map<String, Object>>> certificateExpiring(
            @RequestParam(defaultValue = "30") int warnDays) {
        return Result.success(statsService.countExpiringCertificates(warnDays));
    }

    /** 迁入按月。 */
    @RequiresPermission("stats:query")
    @Operation(summary = "迁入按月统计（默认 12 个月）")
    @GetMapping("/migration/in")
    public Result<List<Map<String, Object>>> migrationIn(
            @RequestParam(defaultValue = "12") int months) {
        return Result.success(statsService.migrationInByMonth(months));
    }

    /** 迁出按月。 */
    @RequiresPermission("stats:query")
    @Operation(summary = "迁出按月统计（默认 12 个月）")
    @GetMapping("/migration/out")
    public Result<List<Map<String, Object>>> migrationOut(
            @RequestParam(defaultValue = "12") int months) {
        return Result.success(statsService.migrationOutByMonth(months));
    }

    /** 重点人口按类型。 */
    @RequiresPermission("stats:query")
    @Operation(summary = "重点人口按类型分组")
    @GetMapping("/key-population/type")
    public Result<List<Map<String, Object>>> keyPopulationType() {
        return Result.success(statsService.countByKeyPopulationType());
    }

    /** 待审批业务申请按类型。 */
    @RequiresPermission("stats:query")
    @Operation(summary = "待审批业务申请按类型")
    @GetMapping("/pending-approvals")
    public Result<List<Map<String, Object>>> pendingApprovals() {
        return Result.success(statsService.pendingApprovalsByType());
    }
}
