package com.example.population.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.population.entity.SysApprovalLog;
import com.example.population.mapper.SysApprovalLogMapper;
import com.example.population.service.SysApprovalLogService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SysApprovalLogServiceImpl extends ServiceImpl<SysApprovalLogMapper, SysApprovalLog>
        implements SysApprovalLogService {

    @Override
    public List<SysApprovalLog> listByApproval(Long approvalId) {
        return this.list(new LambdaQueryWrapper<SysApprovalLog>()
                .eq(SysApprovalLog::getApprovalId, approvalId)
                .orderByAsc(SysApprovalLog::getStepNo));
    }
}