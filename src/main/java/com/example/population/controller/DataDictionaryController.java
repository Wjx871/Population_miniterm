package com.example.population.controller;

import com.example.population.annotation.RequiresPermission;
import com.example.population.dto.DataDictionaryCreateDTO;
import com.example.population.dto.DataDictionaryUpdateDTO;
import com.example.population.dto.Result;
import com.example.population.entity.DataDictionary;
import com.example.population.service.DataDictionaryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "数据字典")
@RestController
@RequestMapping("/api/data-dictionaries")
@RequiredArgsConstructor
public class DataDictionaryController {

    private final DataDictionaryService dictionaryService;

    @RequiresPermission("dictionary:query")
    @Operation(summary = "按类型查询字典")
    @GetMapping("/by-type/{type}")
    public Result<List<DataDictionary>> listByType(@PathVariable String type,
                                                   @RequestParam(defaultValue = "false") boolean enabledOnly) {
        return Result.success(enabledOnly
                ? dictionaryService.listEnabledByType(type)
                : dictionaryService.listByType(type));
    }

    @RequiresPermission("dictionary:query")
    @Operation(summary = "直读字典 label")
    @GetMapping("/label")
    public Result<Map<String, String>> label(@RequestParam String type, @RequestParam String code) {
        Map<String, String> body = new HashMap<>();
        body.put("label", dictionaryService.getLabel(type, code));
        return Result.success(body);
    }

    @RequiresPermission("dictionary:query")
    @Operation(summary = "查询单个字典项")
    @GetMapping("/{id}")
    public Result<DataDictionary> get(@PathVariable Long id) {
        return Result.success(dictionaryService.getById(id));
    }

    @RequiresPermission("dictionary:manage")
    @Operation(summary = "新增字典项（白名单字段）")
    @PostMapping
    public Result<Void> create(@Valid @RequestBody DataDictionaryCreateDTO dto) {
        DataDictionary dict = new DataDictionary();
        BeanUtils.copyProperties(dto, dict);
        if (dict.getStatus() == null) dict.setStatus("ENABLED");
        dictionaryService.save(dict);
        return Result.success();
    }

    @RequiresPermission("dictionary:manage")
    @Operation(summary = "更新字典项（白名单字段）")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody DataDictionaryUpdateDTO dto) {
        DataDictionary dict = new DataDictionary();
        BeanUtils.copyProperties(dto, dict);
        dict.setDictId(id);
        dictionaryService.updateById(dict);
        return Result.success();
    }

    @RequiresPermission("dictionary:manage")
    @Operation(summary = "禁用：删除字典项（业务上请改 DISABLED 状态）")
    @DeleteMapping("/{id}")
    public Result<Void> remove(@PathVariable Long id) {
        dictionaryService.removeById(id);
        return Result.success();
    }
}