package com.wjx871.population.query;

import com.wjx871.population.security.DataScopeCriteria;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ComprehensiveQueryMapper {
    long countSummaries(@Param("keyword") String keyword, @Param("personStatus") String personStatus,
            @Param("regionCode") String regionCode, @Param("residenceStatus") String residenceStatus,
            @Param("floatingStatus") String floatingStatus, @Param("permitStatus") String permitStatus,
            @Param("scope") DataScopeCriteria scope);

    List<ComprehensivePersonSummaryView> selectSummaries(@Param("keyword") String keyword,
            @Param("personStatus") String personStatus, @Param("regionCode") String regionCode,
            @Param("residenceStatus") String residenceStatus, @Param("floatingStatus") String floatingStatus,
            @Param("permitStatus") String permitStatus, @Param("scope") DataScopeCriteria scope,
            @Param("orderBy") String orderBy, @Param("direction") String direction,
            @Param("limit") int limit, @Param("offset") long offset);

    Optional<ComprehensivePersonSummaryView> selectScopedSummary(@Param("personId") Long personId,
            @Param("scope") DataScopeCriteria scope);

    Optional<CurrentHouseholdView> selectCurrentHousehold(@Param("personId") Long personId);
    Optional<CurrentResidenceView> selectCurrentResidence(@Param("personId") Long personId);
    Optional<CurrentFloatingView> selectActiveFloating(@Param("personId") Long personId);
    Optional<CurrentPermitView> selectCurrentPermit(@Param("personId") Long personId);
    List<MigrationHistoryView> selectMigrationHistory(@Param("personId") Long personId, @Param("limit") int limit);
}
