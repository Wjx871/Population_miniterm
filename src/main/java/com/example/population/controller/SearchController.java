package com.example.population.controller;

import com.example.population.annotation.RequiresPermission;
import com.example.population.dto.Result;
import com.example.population.dto.SearchResultDTO;
import com.example.population.exception.BizException;
import com.example.population.service.UnifiedSearchService;
import com.example.population.util.MaskedSerializer;
import com.example.population.util.SecurityContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * §2.2.9 综合查询统一入口。
 *
 * <p>对应业务流程 §2.2.9：
 * <ul>
 *   <li>"用户进入综合查询……选择查询条件……系统根据查询条件动态组合检索条件"——通过 {@code ?keyword=} 一个入口覆盖 7 类实体</li>
 *   <li>"系统从相关数据表中查询有效数据"——内部复用各模块的 {@code page(...)}，自动套用
 *       {@code @DataScope} 区划/部门过滤以及逻辑删除（{@code is_deleted=0}）</li>
 *   <li>"系统返回符合条件的数据列表"——按实体分桶返回，前端按 bucket 单独渲染</li>
 * </ul>
 * </p>
 *
 * <p><b>权限</b>：
 * <ul>
 *   <li>统一使用 {@code query:comprehensive}（在角色配置中可绑定到查询统计人员 / 户籍管理人员 / 人口信息管理人员）</li>
 *   <li>敏感字段（身份证号、手机号、证件号）走全局 {@link com.example.population.util.MaskedSerializer}：默认脱敏，
 *       仅 L3 角色 + 显式传 {@code ?unmask=true} 才输出原文（与 §Person 业务一致）</li>
 * </ul>
 * </p>
 */
@Slf4j
@Tag(name = "综合查询")
@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class SearchController {

    private final UnifiedSearchService unifiedSearchService;

    /**
     * 综合查询统一入口。
     *
     * <p>必传 {@code keyword}（≤64 字符）。其他字段：
     * <ul>
     *   <li>{@code limitPerSource}：每个 bucket 的最大返回条数（默认 10，≤50）</li>
     *   <li>{@code unmask}：仅当角色 ≥ L3 时生效，控制敏感字段是否脱敏</li>
     * </ul>
     * </p>
     *
     * @param keyword        用户输入的关键字
     * @param limitPerSource 每个 bucket 的最大返回条数
     * @param unmask         是否脱敏（默认 false；仅 L3 + true 才输出原文）
     * @return {@link SearchResultDTO}，每个 bucket 包含命中列表与总数
     */
    @RequiresPermission("query:comprehensive")
    @Operation(summary = "综合查询（跨实体聚合：人员/户口/证件/流动/重点/迁入/迁出）")
    @GetMapping
    public Result<SearchResultDTO> search(
            @Parameter(description = "查询关键字（必填，≤64 字符）", required = true)
            @RequestParam String keyword,
            @Parameter(description = "每类实体最大返回条数（默认 10，≤50）")
            @RequestParam(defaultValue = "10") int limitPerSource,
            @Parameter(description = "敏感字段是否输出原文（仅 L3 角色 + true 生效；默认脱敏）")
            @RequestParam(value = "unmask", required = false) Boolean unmask) {

        if (keyword == null || keyword.trim().isEmpty()) {
            throw new BizException(400, "关键字不能为空");
        }
        if (keyword.length() > 64) {
            throw new BizException(400, "关键字过长（最长 64 字符）");
        }

        // P0: 仅 L3 + 显式 unmask=true 才跳过脱敏；其他一律脱敏。
        if (Boolean.TRUE.equals(unmask)) {
            SecurityContext sc = SecurityContext.current();
            if (sc != null && sc.getPermissionLevel() != null && sc.getPermissionLevel() >= 3) {
                MaskedSerializer.UNMASK.set(Boolean.TRUE);
            }
        }

        try {
            SearchResultDTO result = unifiedSearchService.unifiedSearch(keyword, limitPerSource);
            return Result.success("综合查询完成", result);
        } finally {
            MaskedSerializer.UNMASK.remove();
        }
    }
}