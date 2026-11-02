package com.wjx871.population.stats;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import com.wjx871.population.security.DataScopeCriteria;

/**
 * 统计信息 Mapper 接口。
 *
 * @author Gem
 * @date 2026/07/08
 */
@Mapper
public interface StatisticsMapper {

    /**
     * 获取常驻人口总数。
     */
    long countActivePersons(@Param("scope") DataScopeCriteria scope);

    /**
     * 获取流动人口数。
     */
    long countFloatingPopulation(@Param("scope") DataScopeCriteria scope);

    /**
     * 获取重点关注人口数。
     */
    long countKeyPopulation(@Param("scope") DataScopeCriteria scope);

    /**
     * 获取家庭户总数。
     */
    long countHouseholds(@Param("scope") DataScopeCriteria scope);

    /**
     * 按重点人口分类分组统计。
     */
    List<Map<String, Object>> countKeyPopulationByType(@Param("scope") DataScopeCriteria scope);

    /**
     * 按证件状态统计。
     */
    List<Map<String, Object>> countCertificateByStatus(@Param("scope") DataScopeCriteria scope);

    /**
     * 获取近7天迁入人数统计。
     */
    List<Map<String, Object>> countRecentMigrationIn(@Param("from") LocalDate from,
            @Param("scope") DataScopeCriteria scope);

    /**
     * 获取近7天迁出人数统计。
     */
    List<Map<String, Object>> countRecentMigrationOut(@Param("from") LocalDate from,
            @Param("scope") DataScopeCriteria scope);

    /**
     * 获取最近的操作日志（前10条）。
     */
    List<Map<String, Object>> selectRecentLogs(@Param("scope") DataScopeCriteria scope);
}
