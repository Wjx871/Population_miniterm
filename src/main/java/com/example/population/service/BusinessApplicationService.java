package com.example.population.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.example.population.entity.BusinessApplication;

public interface BusinessApplicationService extends IService<BusinessApplication> {

    IPage<BusinessApplication> page(long current, long size, String keyword, String status,
                                    String businessTypeCode, Long submitUserId, Long handlingDepartmentId);

    BusinessApplication getDetail(Long applicationId);

    boolean submit(Long applicationId);
}