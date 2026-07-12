package com.example.population.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.population.annotation.RequiresPermission;
import com.example.population.dto.BusinessApplicationCreateDTO;
import com.example.population.dto.BusinessApplicationUpdateDTO;
import com.example.population.dto.PageVO;
import com.example.population.dto.Result;
import com.example.population.entity.BusinessApplication;
import com.example.population.service.BusinessApplicationService;
import com.example.population.util.PageUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

@Tag(name = "业务申请")
@RestController
@RequestMapping("/api/business-applications")
@RequiredArgsConstructor
public class BusinessApplicationController {

    private final BusinessApplicationService applicationService;

    @RequiresPermission("application:query")
    @Operation(summary = "分页查询")
    @GetMapping
    public Result<PageVO<BusinessApplication>> page(@RequestParam(defaultValue = "1") long current,
                                                     @RequestParam(defaultValue = "10") long size,
                                                     @RequestParam(required = false) String keyword,
                                                     @RequestParam(required = false) String status,
                                                     @RequestParam(required = false) String businessTypeCode,
                                                     @RequestParam(required = false) Long submitUserId,
                                                     @RequestParam(required = false) Long handlingDepartmentId) {
        Page<BusinessApplication> p = (Page<BusinessApplication>) applicationService.page(
                current, size, keyword, status, businessTypeCode, submitUserId, handlingDepartmentId);
        return Result.success(PageUtil.toPageVO(p, p.getRecords()));
    }

    @RequiresPermission("application:query")
    @Operation(summary = "查询详情")
    @GetMapping("/{id}")
    public Result<BusinessApplication> get(@PathVariable Long id) {
        return Result.success(applicationService.getDetail(id));
    }

    @RequiresPermission("application:manage")
    @Operation(summary = "新增申请（白名单字段）")
    @PostMapping
    public Result<Void> create(@Valid @RequestBody BusinessApplicationCreateDTO dto) {
        BusinessApplication app = new BusinessApplication();
        BeanUtils.copyProperties(dto, app);
        applicationService.save(app);
        return Result.success();
    }

    @RequiresPermission("application:manage")
    @Operation(summary = "更新申请（白名单字段；仅在 DRAFT 状态允许）")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody BusinessApplicationUpdateDTO dto) {
        BusinessApplication app = new BusinessApplication();
        BeanUtils.copyProperties(dto, app);
        app.setApplicationId(id);
        applicationService.updateById(app);
        return Result.success();
    }

    @RequiresPermission("application:manage")
    @Operation(summary = "提交申请")
    @PutMapping("/{id}/submit")
    public Result<Void> submit(@PathVariable Long id) {
        applicationService.submit(id);
        return Result.success();
    }

    @RequiresPermission("application:manage")
    @Operation(summary = "禁用：删除申请（审计要求保留，SUBMITTED 后禁止）")
    @DeleteMapping("/{id}")
    public Result<Void> remove(@PathVariable Long id) {
        applicationService.removeById(id);
        return Result.success();
    }
}