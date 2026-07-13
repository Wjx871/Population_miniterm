package com.example.population.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 统计看板/大屏查询服务。
 *
 * <p>对应业务流程 §2.2.9 / §2.2.10，提供：
 * <ul>
 *   <li>人员/户口/证件/迁入迁出等的多维度汇总</li>
 *   <li>统计看板卡片（数字卡）</li>
 *   <li>前端图表（饼图、柱状、折线）所需的分组数据</li>
 * </ul>
 *
 * <p><b>安全</b>：所有查询都在 SQL 层完成汇总，不返回明细；身份证号/手机号等敏感字段不参与统计。
 */
public interface StatsService {

    /** 人员档案状态分组（ACTIVE/CANCELLED）。 */
    List<Map<String, Object>> countByPersonStatus(LocalDate startDate, LocalDate endDate);

    /** 人员性别分组。 */
    List<Map<String, Object>> countByPersonGender();

    /** 民族 Top N。 */
    List<Map<String, Object>> countByPersonEthnicity(int topN);

    /** 户口类型分组。 */
    List<Map<String, Object>> countByHouseholdType();

    /** 户口状态分组。 */
    List<Map<String, Object>> countByHouseholdStatus();

    /** 区划当前户籍人口数。 */
    List<Map<String, Object>> populationByRegion();

    /** 流动人口按区划（默认 ACTIVE）。 */
    List<Map<String, Object>> floatingByRegion(String status);

    /** 证件状态分组。 */
    List<Map<String, Object>> countByCertificateStatus();

    /** 证件到期/过期（warnDays 默认为 30）。 */
    List<Map<String, Object>> countExpiringCertificates(int warnDays);

    /** 迁入按月统计（默认最近 12 个月）。 */
    List<Map<String, Object>> migrationInByMonth(int months);

    /** 迁出按月统计。 */
    List<Map<String, Object>> migrationOutByMonth(int months);

    /** 重点人口按类型。 */
    List<Map<String, Object>> countByKeyPopulationType();

    /** 综合数字卡（一次性返回所有卡）。 */
    Map<String, Object> dashboardCounters();

    /** 待审批业务申请按类型。 */
    List<Map<String, Object>> pendingApprovalsByType();
}
