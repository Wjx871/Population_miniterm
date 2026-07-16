package com.wjx871.population.dashboard;

import com.wjx871.population.security.DataScopeCriteria;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DashboardMapper {
    long countRegisteredPopulation(@Param("scope") DataScopeCriteria scope);
    long countActiveFloating(@Param("scope") DataScopeCriteria scope);
    long countActivePermits(@Param("scope") DataScopeCriteria scope);
    long countPendingApprovals(@Param("scope") DataScopeCriteria scope);
    long countExpiringPermits(@Param("from") LocalDate from, @Param("to") LocalDate to, @Param("scope") DataScopeCriteria scope);
    long countMigrationsIn(@Param("from") LocalDate from, @Param("to") LocalDate to, @Param("scope") DataScopeCriteria scope);
    long countMigrationsOut(@Param("from") LocalDate from, @Param("to") LocalDate to, @Param("scope") DataScopeCriteria scope);
    List<MigrationTrendPoint> migrationInTrend(@Param("from") LocalDate from, @Param("to") LocalDate to, @Param("scope") DataScopeCriteria scope);
    List<MigrationTrendPoint> migrationOutTrend(@Param("from") LocalDate from, @Param("to") LocalDate to, @Param("scope") DataScopeCriteria scope);
    List<NamedCountView> permitStatusDistribution(@Param("scope") DataScopeCriteria scope);
    List<RegionCountView> registeredPopulationByRegion(@Param("scope") DataScopeCriteria scope, @Param("limit") int limit);
    List<NamedCountView> registeredPopulationGender(@Param("scope") DataScopeCriteria scope);
    List<NamedCountView> registeredPopulationAgeGroups(@Param("today") LocalDate today,
            @Param("scope") DataScopeCriteria scope);
    long countActiveKeyPopulation(@Param("scope") DataScopeCriteria scope);
    long countPendingCancellation(@Param("scope") DataScopeCriteria scope);
    long countPendingSensitiveExport(@Param("scope") DataScopeCriteria scope);
    List<NamedCountView> businessScale(@Param("from") LocalDate from, @Param("to") LocalDate to,
            @Param("scope") DataScopeCriteria scope);
    List<PopulationScaleTrendPoint> populationScaleTrend(@Param("from") LocalDate from,
            @Param("to") LocalDate to, @Param("scope") DataScopeCriteria scope);
}
