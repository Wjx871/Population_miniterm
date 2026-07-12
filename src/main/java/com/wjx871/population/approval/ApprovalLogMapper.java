package com.wjx871.population.approval; import java.util.List; import org.apache.ibatis.annotations.Mapper;
@Mapper public interface ApprovalLogMapper {int insert(ApprovalLog log);List<ApprovalLog> selectByApplicationId(Long applicationId);}
