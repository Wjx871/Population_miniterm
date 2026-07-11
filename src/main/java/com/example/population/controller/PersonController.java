package com.example.population.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.population.dto.PageVO;
import com.example.population.dto.PersonCreateDTO;
import com.example.population.dto.PersonQueryDTO;
import com.example.population.dto.PersonUpdateDTO;
import com.example.population.dto.Result;
import com.example.population.entity.Person;
import com.example.population.service.PersonService;
import com.example.population.util.IdCardValidator;
import com.example.population.util.PageUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Tag(name = "人口档案")
@RestController
@RequestMapping("/api/persons")
@RequiredArgsConstructor
public class PersonController {

    private final PersonService personService;

    @Operation(summary = "分页查询（多条件）")
    @GetMapping
    public Result<PageVO<Person>> page(PersonQueryDTO q) {
        Page<Person> p = (Page<Person>) personService.queryPage(q);
        return Result.success(PageUtil.toPageVO(p, p.getRecords()));
    }

    @Operation(summary = "查询单个")
    @GetMapping("/{id}")
    public Result<Person> get(@PathVariable Long id) {
        return Result.success(personService.getById(id));
    }

    @Operation(summary = "按证件类型+号码查询")
    @GetMapping("/identity")
    public Result<Person> getByIdentity(@RequestParam String type, @RequestParam String no) {
        return Result.success(personService.getByIdentity(type, no));
    }

    @Operation(summary = "实时校验身份证号（GB 11643-1999）")
    @GetMapping("/validate/identity")
    public Result<Map<String, Object>> validateIdentity(@RequestParam String no) {
        Map<String, Object> data = new HashMap<>();
        data.put("valid", IdCardValidator.isValid(no));
        data.put("birthday", IdCardValidator.extractBirthday(no));
        data.put("genderCode", IdCardValidator.extractGenderCode(no));
        return Result.success(data);
    }

    @Operation(summary = "新增人口")
    @PostMapping
    public Result<Person> create(@Valid @RequestBody PersonCreateDTO dto) {
        return Result.success("创建成功", personService.createPerson(dto));
    }

    @Operation(summary = "更新人口基础信息")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody PersonUpdateDTO dto) {
        personService.updatePerson(id, dto);
        return Result.success();
    }

    @Operation(summary = "删除（软删，逻辑删除）")
    @DeleteMapping("/{id}")
    public Result<Void> remove(@PathVariable Long id) {
        personService.removeById(id);
        return Result.success();
    }
}
