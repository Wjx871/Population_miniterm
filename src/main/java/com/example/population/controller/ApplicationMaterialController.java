package com.example.population.controller;

import com.example.population.annotation.RequiresPermission;
import com.example.population.dto.ApplicationMaterialCreateDTO;
import com.example.population.dto.ApplicationMaterialUpdateDTO;
import com.example.population.dto.Result;
import com.example.population.entity.ApplicationMaterial;
import com.example.population.service.ApplicationMaterialService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "申请材料")
@RestController
@RequestMapping("/api/application-materials")
@RequiredArgsConstructor
public class ApplicationMaterialController {

    private final ApplicationMaterialService materialService;

    @RequiresPermission("material:query")
    @Operation(summary = "按申请查询材料")
    @GetMapping("/by-application/{applicationId}")
    public Result<List<ApplicationMaterial>> list(@PathVariable Long applicationId) {
        return Result.success(materialService.listByApplication(applicationId));
    }

    @RequiresPermission("material:query")
    @Operation(summary = "查询单个")
    @GetMapping("/{id}")
    public Result<ApplicationMaterial> get(@PathVariable Long id) {
        return Result.success(materialService.getById(id));
    }

    @RequiresPermission("material:manage")
    @Operation(summary = "新增材料（白名单字段）")
    @PostMapping
    public Result<Void> create(@Valid @RequestBody ApplicationMaterialCreateDTO dto) {
        ApplicationMaterial m = new ApplicationMaterial();
        BeanUtils.copyProperties(dto, m);
        materialService.save(m);
        return Result.success();
    }

    @RequiresPermission("material:manage")
    @Operation(summary = "更新材料（白名单字段；仅 UNVERIFIED 允许）")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody ApplicationMaterialUpdateDTO dto) {
        ApplicationMaterial m = new ApplicationMaterial();
        BeanUtils.copyProperties(dto, m);
        m.setMaterialId(id);
        materialService.updateById(m);
        return Result.success();
    }

    @RequiresPermission("material:verify")
    @Operation(summary = "核验材料")
    @PutMapping("/{id}/verify")
    public Result<Void> verify(@PathVariable Long id,
                                @RequestParam Long verifierId,
                                @RequestParam(defaultValue = "true") boolean passed) {
        materialService.verify(id, verifierId, passed);
        return Result.success();
    }

    @RequiresPermission("material:manage")
    @Operation(summary = "删除材料（仅 UNVERIFIED 允许）")
    @DeleteMapping("/{id}")
    public Result<Void> remove(@PathVariable Long id) {
        materialService.removeById(id);
        return Result.success();
    }
}