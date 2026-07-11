package com.example.population.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.population.entity.SysApprovalLog;
import com.example.population.entity.SysApprovalRequest;
import com.example.population.mapper.SysApprovalLogMapper;
import com.example.population.mapper.SysApprovalRequestMapper;
import com.example.population.service.SysApprovalRequestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class SysApprovalRequestServiceImpl extends ServiceImpl<SysApprovalRequestMapper, SysApprovalRequest>
        implements SysApprovalRequestService {

    private final SysApprovalLogMapper approvalLogMapper;

    @Override
    public IPage<SysApprovalRequest> page(long current, long size, String status, Long currentApproverId) {
        Page<SysApprovalRequest> page = new Page<>(current, size);
        LambdaQueryWrapper<SysApprovalRequest> w = new LambdaQueryWrapper<>();
        if (status != null && !status.isEmpty()) {
            w.eq(SysApprovalRequest::getStatus, status);
        }
        if (currentApproverId != null) {
            w.eq(SysApprovalRequest::getCurrentApproverId, currentApproverId);
        }
        w.orderByDesc(SysApprovalRequest::getSubmittedAt);
        return this.page(page, w);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean approve(Long approvalId, Long approverId, String comment) {
        SysApprovalRequest req = this.getById(approvalId);
        if (req == null || !"PENDING".equalsIgnoreCase(req.getStatus())) {
            return false;
        }
        Long nextStep = approvalLogMapper.selectCount(new LambdaQueryWrapper<SysApprovalLog>()
                .eq(SysApprovalLog::getApprovalId, approvalId)) + 1;
        SysApprovalLog log = new SysApprovalLog();
        log.setApprovalId(approvalId);
        log.setStepNo(nextStep.intValue());
        log.setApproverUserId(approverId);
        log.setActionCode("APPROVE");
        log.setComment(comment);
        log.setApprovedAt(LocalDateTime.now());
        approvalLogMapper.insert(log);

        req.setStatus("APPROVED");
        req.setFinishedAt(LocalDateTime.now());
        return this.updateById(req);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean reject(Long approvalId, Long approverId, String comment) {
        SysApprovalRequest req = this.getById(approvalId);
        if (req == null || !"PENDING".equalsIgnoreCase(req.getStatus())) {
            return false;
        }
        Long nextStep = approvalLogMapper.selectCount(new LambdaQueryWrapper<SysApprovalLog>()
                .eq(SysApprovalLog::getApprovalId, approvalId)) + 1;
        SysApprovalLog log = new SysApprovalLog();
        log.setApprovalId(approvalId);
        log.setStepNo(nextStep.intValue());
        log.setApproverUserId(approverId);
        log.setActionCode("REJECT");
        log.setComment(comment);
        log.setApprovedAt(LocalDateTime.now());
        approvalLogMapper.insert(log);

        req.setStatus("REJECTED");
        req.setFinishedAt(LocalDateTime.now());
        return this.updateById(req);
    }
}