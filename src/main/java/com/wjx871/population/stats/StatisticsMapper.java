package com.wjx871.population.stats;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
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
    long countActivePersons();

    /**
     * 获取流动人口数。
     */
    long countFloatingPopulation();

    /**
     * 获取重点关注人口数。
     */
    long countKeyPopulation();

    /**
     * 获取家庭户总数。
     */
    long countHouseholds();

    /**
     * 按重点人口分类分组统计。
     */
    List<Map<String, Object>> countKeyPopulationByType();

    /**
     * 按证件状态统计。
     */
    List<Map<String, Object>> countCertificateByStatus();

    /**
     * 获取近7天迁入人数统计。
     */
    List<Map<String, Object>> countRecentMigrationIn();

    /**
     * 获取近7天迁出人数统计。
     */
    List<Map<String, Object>> countRecentMigrationOut();

    /**
     * 获取最近的操作日志（前10条）。
     */
    List<Map<String, Object>> selectRecentLogs(DataScopeCriteria criteria);
}
