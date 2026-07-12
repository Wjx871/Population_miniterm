package com.example.population.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.population.annotation.RequiresLevel;
import com.example.population.annotation.RequiresPermission;
import com.example.population.dto.PageVO;
import com.example.population.dto.Result;
import com.example.population.entity.LoginLog;
import com.example.population.service.LoginLogService;
import com.example.population.util.PageUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@Tag(name = "登录日志")
@RestController
@RequestMapping("/api/login-logs")
@RequiredArgsConstructor
public class LoginLogController {

    private final LoginLogService loginLogService;

    @RequiresPermission("loginLog:query")
    @Operation(summary = "分页查询登录日志")
    @GetMapping
    public Result<PageVO<LoginLog>> page(@RequestParam(defaultValue = "1") long current,
                                         @RequestParam(defaultValue = "10") long size,
                                         @RequestParam(required = false) Long userId,
                                         @RequestParam(required = false) String username,
                                         @RequestParam(required = false) String loginStatus,
                                         @RequestParam(required = false)
                                         @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
                                         @RequestParam(required = false)
                                         @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        Page<LoginLog> p = (Page<LoginLog>) loginLogService.page(current, size, userId, username,
                loginStatus, startTime, endTime);
        return Result.success(PageUtil.toPageVO(p, p.getRecords()));
    }

    @RequiresPermission("loginLog:query")
    @Operation(summary = "查询单条登录日志")
    @GetMapping("/{id}")
    public Result<LoginLog> get(@PathVariable Long id) {
        return Result.success(loginLogService.getById(id));
    }

    @RequiresLevel(3)
    @RequiresPermission("loginLog:delete")
    @Operation(summary = "删除单条登录日志（L3 审批级，仅清理历史用）")
    @DeleteMapping("/{id}")
    public Result<Boolean> delete(@PathVariable Long id) {
        return Result.success(loginLogService.removeById(id));
    }
}
