package com.example.population.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 统计看板/大屏查询 Mapper。
 *
 * <p>所有方法返回 Map&lt;String,Object&gt; 列表，与前端 ECharts / 表格直接对接；
 * 不写实体类是为了避免对业务表的"统计维度"做反范式建模。</p>
 *
 * <p><b>设计原则</b>：
 * <ol>
 *   <li>汇总/计数都在 SQL 层完成，绝不在 Java 内存里 group</li>
 *   <li>所有维度字段（region_code、status、gender_code 等）都走字典 code，前端通过字典接口渲染中文</li>
 *   <li>范围查询参数（startDate/endDate）允许为空；为空时不加条件</li>
 *   <li>统计维度由调用方指定，避免暴露过多组合接口</li>
 * </ol>
 */
@Mapper
public interface StatsMapper {

    /**
     * 按人口档案状态分组计数。
     * 返回 [{code: 'ACTIVE', count: 1000}, {code: 'CANCELLED', count: 12}]
     */
    List<Map<String, Object>> countByPersonRecordStatus(@Param("startDate") LocalDate startDate,
                                                        @Param("endDate") LocalDate endDate);

    /**
     * 按人口性别分组计数。
     * 返回 [{code: 'MALE', count: 500}, {code: 'FEMALE', count: 500}, {code: 'UNKNOWN', count: 12}]
     */
    List<Map<String, Object>> countByPersonGender();

    /**
     * 按人口民族分组计数（Top N）。
     */
    List<Map<String, Object>> countByPersonEthnicity(@Param("topN") Integer topN);

    /**
     * 按户口类型分组计数。
     * 返回 [{code: 'FAMILY', count: 800}, {code: 'COLLECTIVE', count: 50}]
     */
    List<Map<String, Object>> countByHouseholdType();

    /**
     * 按户口状态分组计数。
     * 返回 [{code: 'ACTIVE', count: 850}, {code: 'CANCELLED', count: 5}]
     */
    List<Map<String, Object>> countByHouseholdStatus();

    /**
     * 按区划分组当前户籍人口数（用于区划人口统计大屏）。
     * 返回 [{regionCode: '110101', regionName: '东城区', currentPopulation: 12345}, ...]
     */
    List<Map<String, Object>> sumCurrentPopulationByRegion();

    /**
     * 按区划分组流动人口数。
     */
    List<Map<String, Object>> sumFloatingByRegion(@Param("status") String status);

    /**
     * 按证件状态分组计数（人员证件）。
     * 返回 [{code: 'VALID', count: 800}, {code: 'EXPIRING', count: 100}, ...]
     */
    List<Map<String, Object>> countByCertificateStatus();

    /**
     * 证件 30 日内到期 / 已过期 数量。
     * 返回 [{code: 'EXPIRING', count: 50}, {code: 'EXPIRED', count: 20}]
     */
    List<Map<String, Object>> countExpiringCertificates(@Param("warnDays") Integer warnDays);

    /**
     * 按月份统计迁入数量（最近 12 个月）。
     * 返回 [{month: '2025-08', count: 12}, ...]
     */
    List<Map<String, Object>> countMigrationInByMonth(@Param("sinceMonth") String sinceMonth);

    /**
     * 按月份统计迁出数量（最近 12 个月）。
     */
    List<Map<String, Object>> countMigrationOutByMonth(@Param("sinceMonth") String sinceMonth);

    /**
     * 重点人口按类型分组计数。
     */
    List<Map<String, Object>> countByKeyPopulationType();

    /**
     * 综合数字：人口总数、户总数、当前户籍数、流动人口数、重点人口数、待审批数。
     * 返回 [{code: 'PERSON_TOTAL', value: 10000}, {code: 'HOUSEHOLD_TOTAL', value: 3000}, ...]
     */
    List<Map<String, Object>> summaryCounters();

    /**
     * 待审批业务申请数（按 business_type 拆分）。
     * 返回 [{code: 'MIGRATION_IN', count: 5}, {code: 'HOUSEHOLD_ESTABLISH', count: 2}, ...]
     */
    List<Map<String, Object>> countPendingApprovalsByType();
}
