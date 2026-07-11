package com.example.population.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.population.annotation.RequiresPermission;
import com.example.population.dto.PageVO;
import com.example.population.dto.Result;
import com.example.population.entity.ResidencePermit;
import com.example.population.service.ResidencePermitService;
import com.example.population.util.PageUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "居住凭证")
@RestController
@RequestMapping("/api/residence-permits")
@RequiredArgsConstructor
public class ResidencePermitController {

    private final ResidencePermitService permitService;

    @RequiresPermission("permit:query")
    @Operation(summary = "分页查询")
    @GetMapping
    public Result<PageVO<ResidencePermit>> page(@RequestParam(defaultValue = "1") long current,
                                                 @RequestParam(defaultValue = "10") long size,
                                                 @RequestParam(required = false) Long personId,
                                                 @RequestParam(required = false) String permitTypeCode,
                                                 @RequestParam(required = false) String permitStatus) {
        Page<ResidencePermit> p = (Page<ResidencePermit>) permitService.page(current, size, personId, permitTypeCode, permitStatus);
        return Result.success(PageUtil.toPageVO(p, p.getRecords()));
    }

    @RequiresPermission("permit:query")
    @Operation(summary = "查询单个")
    @GetMapping("/{id}")
    public Result<ResidencePermit> get(@PathVariable Long id) {
        return Result.success(permitService.getById(id));
    }

    @RequiresPermission("permit:apply")
    @Operation(summary = "签发凭证")
    @PostMapping
    public Result<Void> create(@RequestBody ResidencePermit p) {
        permitService.save(p);
        return Result.success();
    }

    @RequiresPermission("permit:apply")
    @Operation(summary = "注销凭证")
    @PutMapping("/{id}/cancel")
    public Result<Void> cancel(@PathVariable Long id) {
        permitService.cancel(id);
        return Result.success();
    }

    @RequiresPermission("permit:apply")
    @Operation(summary = "删除凭证")
    @DeleteMapping("/{id}")
    public Result<Void> remove(@PathVariable Long id) {
        permitService.removeById(id);
        return Result.success();
    }
}
