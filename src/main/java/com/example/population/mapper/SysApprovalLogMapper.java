package com.example.population.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.population.entity.SysApprovalLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SysApprovalLogMapper extends BaseMapper<SysApprovalLog> {

    /**
     * 取指定审批单当前最大 step_no。无记录返回 null。
     */
    Integer selectMaxStepNo(@Param("approvalId") Long approvalId);
}