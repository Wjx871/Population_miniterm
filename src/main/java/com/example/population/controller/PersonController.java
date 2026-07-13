package com.example.population.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.population.annotation.RequiresPermission;
import com.example.population.dto.ApprovalDraftDTO;
import com.example.population.dto.PageVO;
import com.example.population.dto.PersonCreateDTO;
import com.example.population.dto.PersonQueryDTO;
import com.example.population.dto.PersonUpdateDTO;
import com.example.population.dto.PersonVO;
import com.example.population.dto.Result;
import com.example.population.entity.Person;
import com.example.population.service.ApprovalGateService;
import com.example.population.service.PersonService;
import com.example.population.util.IdCardValidator;
import com.example.population.util.MaskedSerializer;
import com.example.population.util.PageUtil;
import com.example.population.util.SecurityContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "人口档案")
@RestController
@RequestMapping("/api/persons")
@RequiredArgsConstructor
public class PersonController {

    private final PersonService personService;
    private final ApprovalGateService approvalGateService;
    private final ObjectMapper objectMapper;

    @RequiresPermission("person:query")
    @Operation(summary = "分页查询（多条件，敏感字段自动脱敏；unmask=true 且为 L3 时输出原文）")
    @GetMapping
    public Result<PageVO<PersonVO>> page(PersonQueryDTO q,
                                         @RequestParam(value = "unmask", required = false) Boolean unmask) {
        applyUnmask(unmask);
        try {
            Page<Person> p = (Page<Person>) personService.queryPage(q);
            List<PersonVO> records = new ArrayList<>(p.getRecords().size());
            for (Person person : p.getRecords()) {
                records.add(PersonVO.from(person));
            }
            PageVO<PersonVO> vo = PageUtil.toPageVO(p, records);
            return Result.success(vo);
        } finally {
            MaskedSerializer.UNMASK.remove();
        }
    }

    @RequiresPermission("person:query")
    @Operation(summary = "查询单个（敏感字段自动脱敏；unmask=true 且为 L3 时输出原文）")
    @GetMapping("/{id}")
    public Result<PersonVO> get(@PathVariable Long id,
                                @RequestParam(value = "unmask", required = false) Boolean unmask) {
        applyUnmask(unmask);
        try {
            return Result.success(PersonVO.from(personService.getById(id)));
        } finally {
            MaskedSerializer.UNMASK.remove();
        }
    }

    @RequiresPermission("person:query")
    @Operation(summary = "按证件类型+号码查询（敏感字段自动脱敏）")
    @GetMapping("/identity")
    public Result<PersonVO> getByIdentity(@RequestParam String type, @RequestParam String no) {
        return Result.success(PersonVO.from(personService.getByIdentity(type, no)));
    }

    @RequiresPermission("person:query")
    @Operation(summary = "实时校验身份证号（GB 11643-1999）")
    @GetMapping("/validate/identity")
    public Result<Map<String, Object>> validateIdentity(@RequestParam String no) {
        Map<String, Object> data = new HashMap<>();
        data.put("valid", IdCardValidator.isValid(no));
        data.put("birthday", IdCardValidator.extractBirthday(no));
        data.put("genderCode", IdCardValidator.extractGenderCode(no));
        return Result.success(data);
    }

    @RequiresPermission({"person:create", "person:register"})
    @Operation(summary = "新增人口（审批流：L3 token 直接落库，L1/L2 返回 approvalId 待审批）")
    @PostMapping
    public Result<Map<String, Object>> create(@Valid @RequestBody PersonCreateDTO dto) throws Exception {
        SecurityContext sc = SecurityContext.current();
        // L3 / ADMIN 直通
        if (sc.getPermissionLevel() != null && sc.getPermissionLevel() >= 3
                && sc.hasPermission("person:create")) {
            Person p = personService.createPerson(dto);
            Map<String, Object> data = new HashMap<>();
            data.put("personId", p.getPersonId());
            data.put("directLanding", true);
            return Result.success("创建成功", data);
        }
        // L1/L2 走审批
        ApprovalDraftDTO draft = new ApprovalDraftDTO();
        draft.setBusinessType("PERSON_CREATE");
        draft.setApplicationId(dto.getApplicationId());
        draft.setPayloadJson(objectMapper.writeValueAsString(dto));
        Long approvalId = approvalGateService.submit(draft);
        Map<String, Object> data = new HashMap<>();
        data.put("approvalId", approvalId);
        data.put("applicationId", dto.getApplicationId());
        data.put("directLanding", false);
        return Result.success("已提交审批，等待 L3 审批", data);
    }

    @RequiresPermission("person:update")
    @Operation(summary = "更新人口基础信息（审批流：L3 直通，L1/L2 走审批）")
    @PutMapping("/{id}")
    public Result<Map<String, Object>> update(@PathVariable Long id, @Valid @RequestBody PersonUpdateDTO dto) throws Exception {
        SecurityContext sc = SecurityContext.current();
        if (sc.getPermissionLevel() != null && sc.getPermissionLevel() >= 3) {
            personService.updatePerson(id, dto);
            Map<String, Object> data = new HashMap<>();
            data.put("personId", id);
            data.put("directLanding", true);
            return Result.success("更新成功", data);
        }
        ApprovalDraftDTO draft = new ApprovalDraftDTO();
        draft.setBusinessType("PERSON_UPDATE");
        draft.setBusinessId(id);
        draft.setPayloadJson(objectMapper.writeValueAsString(dto));
        Long approvalId = approvalGateService.submit(draft);
        Map<String, Object> data = new HashMap<>();
        data.put("approvalId", approvalId);
        data.put("directLanding", false);
        return Result.success("已提交审批，等待 L3 审批", data);
    }

    @RequiresPermission("person:update")
    @Operation(summary = "删除（软删，逻辑删除）")
    @DeleteMapping("/{id}")
    public Result<Void> remove(@PathVariable Long id) {
        personService.removeById(id);
        return Result.success();
    }

    /**
     * P0: 仅 L3 + 显式 unmask=true 才跳过脱敏；其他一律脱敏。
     */
    private void applyUnmask(Boolean unmask) {
        if (Boolean.TRUE.equals(unmask)) {
            SecurityContext sc = SecurityContext.current();
            if (sc != null && sc.getPermissionLevel() != null && sc.getPermissionLevel() >= 3) {
                MaskedSerializer.UNMASK.set(Boolean.TRUE);
            }
        }
    }
}
