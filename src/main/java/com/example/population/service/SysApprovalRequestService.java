package com.example.population.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.example.population.entity.SysApprovalRequest;

public interface SysApprovalRequestService extends IService<SysApprovalRequest> {

    IPage<SysApprovalRequest> page(long current, long size, String status, Long currentApproverId);

    boolean approve(Long approvalId, Long approverId, String comment);

    boolean reject(Long approvalId, Long approverId, String comment);
}