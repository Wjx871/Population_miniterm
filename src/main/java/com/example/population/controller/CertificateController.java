package com.example.population.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

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
    @Operation(summary = "新增证件（白名单字段）")
    @PostMapping
    public Result<Void> create(@Valid @RequestBody CertificateCreateDTO dto) {
        Certificate c = new Certificate();
        BeanUtils.copyProperties(dto, c);
        if (c.getCertificateStatus() == null) c.setCertificateStatus("VALID");
        certificateService.save(c);
        return Result.success();
    }

    @RequiresPermission("certificate:manage")
    @Operation(summary = "更新证件（白名单字段）")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody CertificateUpdateDTO dto) {
        Certificate c = new Certificate();
        BeanUtils.copyProperties(dto, c);
        c.setCertificateId(id);
        certificateService.updateById(c);
        return Result.success();
    }

    @RequiresPermission("certificate:manage")
    @Operation(summary = "禁用：删除证件（审计要求保留）")
    @DeleteMapping("/{id}")
    public Result<Void> remove(@PathVariable Long id) {
        certificateService.removeById(id);
        return Result.success();
    }
}