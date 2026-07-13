package com.example.population.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.population.annotation.LogOperation;
import com.example.population.annotation.RequiresPermission;
import com.example.population.dto.CertificateCreateDTO;
import com.example.population.dto.CertificateUpdateDTO;
import com.example.population.dto.PageVO;
import com.example.population.dto.Result;
import com.example.population.entity.Certificate;
import com.example.population.service.CertificateService;
import com.example.population.util.PageUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

@Tag(name = "通用证件")
@RestController
@RequestMapping("/api/certificates")
@RequiredArgsConstructor
public class CertificateController {

    private final CertificateService certificateService;

    @RequiresPermission("certificate:query")
    @Operation(summary = "分页查询")
    @GetMapping
    public Result<PageVO<Certificate>> page(@RequestParam(defaultValue = "1") long current,
                                             @RequestParam(defaultValue = "10") long size,
                                             @RequestParam(required = false) Long personId,
                                             @RequestParam(required = false) String certificateTypeCode,
                                             @RequestParam(required = false) String certificateStatus) {
        Page<Certificate> p = (Page<Certificate>) certificateService.page(current, size, personId, certificateTypeCode, certificateStatus);
        return Result.success(PageUtil.toPageVO(p, p.getRecords()));
    }

    @RequiresPermission("certificate:query")
    @Operation(summary = "查询单个")
    @GetMapping("/{id}")
    public Result<Certificate> get(@PathVariable Long id) {
        return Result.success(certificateService.getById(id));
    }

    @RequiresPermission("certificate:manage")
    @LogOperation(module = "CERTIFICATE", type = "CREATE", targetTable = "certificate")
    @Operation(summary = "新增证件（含同类同号唯一性 + 自动判定有效/即将到期/已过期）")
    @PostMapping
    public Result<Certificate> create(@Valid @RequestBody CertificateCreateDTO dto) {
        return Result.success("证件登记成功", certificateService.createCertificate(dto));
    }

    @RequiresPermission("certificate:manage")
    @LogOperation(module = "CERTIFICATE", type = "UPDATE", targetTable = "certificate", targetIdSpel = "#id")
    @Operation(summary = "更新证件（白名单字段；自动按 validUntil 重算状态）")
    @PutMapping("/{id}")
    public Result<Certificate> update(@PathVariable Long id, @Valid @RequestBody CertificateUpdateDTO dto) {
        return Result.success("证件更新成功", certificateService.updateCertificate(id, dto));
    }

    @RequiresPermission("certificate:manage")
    @LogOperation(module = "CERTIFICATE", type = "CANCEL", targetTable = "certificate", targetIdSpel = "#id")
    @Operation(summary = "注销证件")
    @PutMapping("/{id}/cancel")
    public Result<Void> cancel(@PathVariable Long id) {
        certificateService.cancelCertificate(id);
        return Result.success();
    }

    @RequiresPermission("certificate:query")
    @Operation(summary = "按 validUntil 自动判定状态（VALID/EXPIRING/EXPIRED）")
    @GetMapping("/resolve-status")
    public Result<Map<String, Object>> resolveStatus(@RequestParam(required = false) String validUntil,
                                                     @RequestParam(defaultValue = "30") int warnDays) {
        LocalDate v = (validUntil == null || validUntil.isBlank()) ? null : LocalDate.parse(validUntil);
        String code = certificateService.resolveStatus(v, warnDays);
        return Result.success(Map.of("status", code, "validUntil", String.valueOf(v)));
    }

    @RequiresPermission("certificate:manage")
    @Operation(summary = "到期扫描：把 valid_until<CURDATE() 的非 CANCELLED 证件自动置 EXPIRED")
    @PostMapping("/scan-expired")
    public Result<Integer> scanExpired() {
        return Result.success("扫描完成", certificateService.scanExpired());
    }

    @RequiresPermission("certificate:query")
    @Operation(summary = "即将到期/已过期清单（默认 30 日内）")
    @GetMapping("/expiring")
    public Result<java.util.List<Certificate>> expiring(@RequestParam(defaultValue = "30") int warnDays) {
        return Result.success(certificateService.listExpiringCertificates(warnDays));
    }

    /**
     * 物理删除不开放（C-19 审计要求保留全部历史）。
     */
    @Operation(summary = "删除（已禁用，按设计文档 C-19 不允许物理删除）", hidden = true)
    @DeleteMapping("/{id}")
    public Result<Void> remove(@PathVariable Long id) {
        throw new com.example.population.exception.BizException(405,
                "证件数据受审计保护（C-19），不允许物理删除");
    }
}