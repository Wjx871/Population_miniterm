package com.example.population.controller;

import com.example.population.dto.HouseholdMemberDTO;
import com.example.population.dto.HouseholdMemberTransferDTO;
import com.example.population.dto.Result;
import com.example.population.entity.HouseholdMember;
import com.example.population.service.HouseholdMemberService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "户-人关系")
@RestController
@RequestMapping("/api/household-members")
@RequiredArgsConstructor
public class HouseholdMemberController {

    private final HouseholdMemberService memberService;

    @Operation(summary = "查询户当前成员")
    @GetMapping("/current/{householdId}")
    public Result<List<HouseholdMember>> currentMembers(@PathVariable Long householdId) {
        return Result.success(memberService.listCurrentMembers(householdId));
    }

    @Operation(summary = "查询个人户关系历史")
    @GetMapping("/history/person/{personId}")
    public Result<List<HouseholdMember>> historyByPerson(@PathVariable Long personId) {
        return Result.success(memberService.listHistoryByPerson(personId));
    }

    @Operation(summary = "新增成员关系")
    @PostMapping
    public Result<HouseholdMember> create(@Valid @RequestBody HouseholdMemberDTO dto) {
        return Result.success("添加成功", memberService.addMember(dto));
    }

    @Operation(summary = "批量过户（同市跨区随迁事务）")
    @PostMapping("/transfer")
    public Result<List<Long>> transfer(@Valid @RequestBody HouseholdMemberTransferDTO dto) {
        return Result.success("过户完成", memberService.transferMembers(dto));
    }

    @Operation(summary = "移除成员关系（迁出/注销）")
    @PutMapping("/{memberId}/leave")
    public Result<Void> leave(@PathVariable Long memberId) {
        memberService.removeMember(memberId);
        return Result.success();
    }

    @Operation(summary = "更新成员关系")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody HouseholdMember m) {
        m.setMemberId(id);
        memberService.updateById(m);
        return Result.success();
    }

    @Operation(summary = "删除成员关系")
    @DeleteMapping("/{id}")
    public Result<Void> remove(@PathVariable Long id) {
        memberService.removeById(id);
        return Result.success();
    }
}
