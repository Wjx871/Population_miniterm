package com.example.population.service;

import com.example.population.dto.SearchResultDTO;

/**
 * §2.2.9 综合查询服务。
 *
 * <p>对外提供一个统一的"跨实体模糊检索"入口，避免前端在查询页面分别调用 7 个业务接口再聚合。
 * 实际内部依然复用各业务模块的 {@code page(...)} 方法，保留：
 * <ul>
 *   <li>{@code SafeLike} 转义（防 % _ LIKE 通配符 DoS）</li>
 *   <li>{@code DataScope} 区划/部门过滤（设计文档 §6：禁止前端绕过权限）</li>
 *   <li>各业务实体的敏感字段脱敏（{@code MaskedSerializer}）</li>
 * </ul>
 * 单源最大返回条数受 {@code limitPerSource} 控制，避免一次搜索拉全表。</p>
 */
public interface UnifiedSearchService {

    /**
     * 跨实体聚合搜索。
     *
     * @param keyword        用户输入关键字（必填，trim+escape；最长 64 字符）
     * @param limitPerSource 每个 bucket 的最大返回条数（≤ 50）
     * @return 每个实体类型的命中列表 + 命中总数
     */
    SearchResultDTO unifiedSearch(String keyword, int limitPerSource);
}
