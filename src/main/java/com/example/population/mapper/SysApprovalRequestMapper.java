package com.example.population.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.population.entity.SysApprovalRequest;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SysApprovalRequestMapper extends BaseMapper<SysApprovalRequest> {

    /**
     * 按主键加行锁，用于审批 / 驳回流程串行化。仅在事务内有效。
     */
    SysApprovalRequest selectByIdForUpdate(@Param("id") Long id);
}