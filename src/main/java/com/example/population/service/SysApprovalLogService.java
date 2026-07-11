package com.example.population.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.population.entity.SysApprovalLog;

import java.util.List;

public interface SysApprovalLogService extends IService<SysApprovalLog> {

    List<SysApprovalLog> listByApproval(Long approvalId);
}