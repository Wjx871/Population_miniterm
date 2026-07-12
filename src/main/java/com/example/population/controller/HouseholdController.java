package com.example.population.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.population.annotation.RequiresPermission;
import com.example.population.dto.ApprovalDraftDTO;
import com.example.population.dto.HouseholdCreateDTO;
import com.example.population.dto.HouseholdUpdateDTO;
import com.example.population.dto.PageVO;
import com.example.population.dto.Result;
import com.example.population.entity.Household;
import com.example.population.service.ApprovalGateService;
import com.example.population.service.HouseholdService;
import com.example.population.util.PageUtil;
import com.example.population.util.SecurityContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Tag(name = "家庭户档案")
@RestController
@RequestMapping("/api/households")
@RequiredArgsConstructor
public class HouseholdController {

    private final HouseholdService householdService;
    private final ApprovalGateService approvalGateService;
    private final ObjectMapper objectMapper;

    @RequiresPermission("household:query")
    @Operation(summary = "分页查询户籍")
    @GetMapping
    public Result<PageVO<Household>> page(@RequestParam(defaultValue = "1") long current,
                                          @RequestParam(defaultValue = "10") long size,
                                          @RequestParam(required = false) String keyword,
                                          @RequestParam(required = false) String regionCode,
                                          @RequestParam(required = false) String status) {
        Page<Household> p = (Page<Household>) householdService.page(current, size, keyword, regionCode, status);
        return Result.success(PageUtil.toPageVO(p, p.getRecords()));
    }

    @RequiresPermission("household:query")
    @Operation(summary = "查询详情（含成员数）")
    @GetMapping("/{id}")
    public Result<Household> detail(@PathVariable Long id) {
        return Result.success(householdService.getDetail(id));
    }

    @RequiresPermission({"household:create", "household:establish"})
    @Operation(summary = "立户（审批流：L3 直通，L1/L2 走审批）")
    @PostMapping("/establish")
    public Result<Map<String, Object>> establish(@Valid @RequestBody HouseholdCreateDTO dto) throws Exception {
        SecurityContext sc = SecurityContext.current();
        if (sc.getPermissionLevel() != null && sc.getPermissionLevel() >= 3
                && sc.hasPermission("household:create")) {
            Household h = householdService.establishHousehold(dto);
            Map<String, Object> data = new HashMap<>();
            data.put("householdId", h == null ? null : h.getHouseholdId());
            data.put("directLanding", true);
            return Result.success("立户成功", data);
        }
        ApprovalDraftDTO draft = new ApprovalDraftDTO();
        draft.setBusinessType("HOUSEHOLD_ESTABLISH");
        draft.setApplicationId(dto.getApplicationId());
        draft.setPayloadJson(objectMapper.writeValueAsString(dto));
        Long approvalId = approvalGateService.submit(draft);
        Map<String, Object> data = new HashMap<>();
        data.put("approvalId", approvalId);
        data.put("applicationId", dto.getApplicationId());
        data.put("directLanding", false);
        return Result.success("已提交审批，等待 L3 审批", data);
    }

    /**
     * 旧 POST / 端点已禁用：避免 Mass Assignment（外部可注入任意 Household 字段）。
     * 新增请走 {@code POST /api/households/establish} 业务流（带 applicationId 材料闸门）。
     */
    @Operation(summary = "禁用：旧 POST / 直接落库，请走 /establish 业务流")
    @PostMapping
    public Result<Void> create() {
        throw new com.example.population.exception.BizException(405,
                "家庭户新增已禁用通用 POST / 接口；请使用 POST /api/households/establish 走业务流（含材料闸门）");
    }

    @RequiresPermission("household:update")
    @Operation(summary = "更新家庭户（白名单字段）")
    @PutMapping("/{id}")
    public Result<Household> update(@PathVariable Long id, @Valid @RequestBody HouseholdUpdateDTO dto) {
        return Result.success(householdService.updateHousehold(id, dto));
    }

    @RequiresPermission("household:update")
    @Operation(summary = "更换户主")
    @PutMapping("/{id}/head")
    public Result<Void> changeHead(@PathVariable Long id, @RequestParam Long newHeadPersonId) {
        householdService.changeHead(id, newHeadPersonId);
        return Result.success();
    }

    @RequiresPermission("cancellation:household")
    @Operation(summary = "销户（停用户；前置校验无 CURRENT 成员）")
    @PutMapping("/{id}/disable")
    public Result<Void> disable(@PathVariable Long id, @RequestParam Long operatorId) {
        householdService.disableHousehold(id, operatorId);
        return Result.success();
    }

    /**
     * 家庭户不再提供物理/逻辑删除端点。销户请走 {@code PUT /{id}/disable}，
     * 否则将抛 405，避免绕过业务工作流直接销毁户档案。
     */
    @Operation(summary = "禁用：不再支持物理/逻辑删除，请走 /disable 销户")
    @DeleteMapping("/{id}")
    public Result<Void> remove(@PathVariable Long id) {
        throw new com.example.population.exception.BizException(405,
                "家庭户删除已禁用，请使用 PUT /api/households/{id}/disable 走销户流程");
    }
}
