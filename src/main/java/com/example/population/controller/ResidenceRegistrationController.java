package com.example.population.controller;

import com.example.population.dto.ResidenceRegisterDTO;
import com.example.population.dto.Result;
import com.example.population.entity.Household;
import com.example.population.entity.Person;
import com.example.population.entity.ResidenceRegistration;
import com.example.population.service.HouseholdService;
import com.example.population.service.PersonService;
import com.example.population.service.ResidenceRegistrationService;
import com.example.population.util.IdentityMasker;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Tag(name = "当前户籍登记")
@RestController
@RequestMapping("/api/residence-registrations")
@RequiredArgsConstructor
public class ResidenceRegistrationController {

    private final ResidenceRegistrationService registrationService;
    private final PersonService personService;
    private final HouseholdService householdService;

    @Operation(summary = "查询个人当前户籍（脱敏）")
    @GetMapping("/by-person/{personId}")
    public Result<Map<String, Object>> getByPerson(@PathVariable Long personId) {
        ResidenceRegistration reg = registrationService.getByPerson(personId);
        if (reg == null) {
            return Result.success(null);
        }
        Person p = personService.getById(personId);
        Household h = householdService.getById(reg.getHouseholdId());
        Map<String, Object> data = new HashMap<>();
        data.put("registration", reg);
        if (p != null) {
            Map<String, Object> personView = new HashMap<>();
            personView.put("personId", p.getPersonId());
            personView.put("name", IdentityMasker.maskName(p.getName()));
            personView.put("identityNoMasked", IdentityMasker.maskIdCard(p.getIdentityNo()));
            personView.put("phoneMasked", IdentityMasker.maskPhone(p.getPhone()));
            personView.put("genderCode", p.getGenderCode());
            personView.put("recordStatusCode", p.getRecordStatusCode());
            data.put("person", personView);
        }
        data.put("household", h);
        return Result.success(data);
    }

    @Operation(summary = "新增户籍登记（事务内一人一条约束）")
    @PostMapping
    public Result<ResidenceRegistration> register(@Valid @RequestBody ResidenceRegisterDTO dto) {
        return Result.success("登记成功", registrationService.register(dto));
    }

    @Operation(summary = "更新户籍登记（极少使用，请走归档+新增）")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody ResidenceRegistration r) {
        r.setRegistrationId(id);
        registrationService.updateById(r);
        return Result.success();
    }

    @Operation(summary = "删除当前户籍登记（业务上请走迁出/注销，此方法仅做诊断）")
    @DeleteMapping("/{id}")
    public Result<Void> remove(@PathVariable Long id) {
        registrationService.removeById(id);
        return Result.success();
    }
}
