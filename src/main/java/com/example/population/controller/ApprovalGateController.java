package com.example.population.controller;

import com.example.population.annotation.RequiresLevel;
import com.example.population.dto.Result;
import com.example.population.service.ApprovalGateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 审批联动专用端点。
 * <p>
 * 区别于 {@code SysApprovalRequestController}（旧"轻量级"审批）：
 * 本 Controller 的 /approve /reject 会真实调用对应 Service 完成业务数据落地。
 * <p>
 * L3 及以上才能访问。
 */
@Tag(name = "审批联动（L3 专用）")
@RestController
@RequestMapping("/api/approval-gate")
@RequiredArgsConstructor
public class ApprovalGateController {

    private final ApprovalGateService approvalGateService;

    @RequiresLevel(3)
    @Operation(summary = "审批通过（落地业务数据）")
    @PostMapping("/approve/{approvalId}")
    public Result<Map<String, Object>> approve(@PathVariable Long approvalId,
                                               @RequestParam(required = false) String comment) {
        Long landedId = approvalGateService.approve(approvalId, comment);
        Map<String, Object> data = new HashMap<>();
        data.put("approvalId", approvalId);
        data.put("landedId", landedId);
        return Result.success("审批通过，已落地", data);
    }

    @RequiresLevel(3)
    @Operation(summary = "审批驳回")
    @PostMapping("/reject/{approvalId}")
    public Result<Void> reject(@PathVariable Long approvalId,
                               @RequestParam(required = false) String comment) {
        approvalGateService.reject(approvalId, comment);
        return Result.success();
    }
}
