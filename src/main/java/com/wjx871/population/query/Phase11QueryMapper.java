package com.wjx871.population.query;

import com.wjx871.population.security.DataScopeCriteria;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface Phase11QueryMapper {
    long countPersons(@Param("q") PersonQueryCriteria q, @Param("scope") DataScopeCriteria scope);
    List<ComprehensivePersonSummaryView> selectPersons(@Param("q") PersonQueryCriteria q,
            @Param("scope") DataScopeCriteria scope, @Param("orderBy") String orderBy,
            @Param("direction") String direction, @Param("limit") int limit, @Param("offset") long offset);
    long countHouseholds(@Param("q") HouseholdQueryCriteria q, @Param("scope") DataScopeCriteria scope);
    List<HouseholdQueryView> selectHouseholds(@Param("q") HouseholdQueryCriteria q,
            @Param("scope") DataScopeCriteria scope, @Param("limit") int limit, @Param("offset") long offset);
    long countMigrations(@Param("q") MigrationQueryCriteria q, @Param("scope") DataScopeCriteria scope);
    List<MigrationQueryView> selectMigrations(@Param("q") MigrationQueryCriteria q,
            @Param("scope") DataScopeCriteria scope, @Param("limit") int limit, @Param("offset") long offset);
}
