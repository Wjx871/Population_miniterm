package com.wjx871.population.application;

import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface BusinessApplicationMapper {
    int insert(BusinessApplication application);
    Optional<BusinessApplication> selectById(Long applicationId);
    long countByQuery(ApplicationQuery query);
    List<BusinessApplication> selectByQuery(ApplicationQuery query);
    int updateDraft(@Param("application") BusinessApplication application, @Param("expectedVersion") int expectedVersion);
    int updateStatus(@Param("applicationId") Long applicationId, @Param("fromStatus") ApplicationStatus fromStatus,
                     @Param("toStatus") ApplicationStatus toStatus, @Param("expectedVersion") int expectedVersion);
    int completeWithTargetPerson(@Param("applicationId") Long applicationId,
                                 @Param("expectedVersion") int expectedVersion,
                                 @Param("targetPersonId") Long targetPersonId);
}
