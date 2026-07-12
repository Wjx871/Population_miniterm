package com.example.population.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.population.entity.BusinessApplication;
import com.example.population.mapper.BusinessApplicationMapper;
import com.example.population.service.BusinessApplicationService;
import com.example.population.util.PageUtil;
import com.example.population.util.SafeLike;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class BusinessApplicationServiceImpl extends ServiceImpl<BusinessApplicationMapper, BusinessApplication>
        implements BusinessApplicationService {

    @Override
    public IPage<BusinessApplication> page(long current, long size, String keyword, String status,
                                           String businessTypeCode, Long submitUserId, Long handlingDepartmentId) {
        Page<BusinessApplication> page = PageUtil.clamp(current, size);
        LambdaQueryWrapper<BusinessApplication> w = new LambdaQueryWrapper<>();
        String safeKw = SafeLike.escape(keyword);
        if (safeKw != null && !safeKw.isEmpty()) {
            w.and(w2 -> w2.like(BusinessApplication::getApplicationNo, safeKw)
                    .or().like(BusinessApplication::getApplicantName, safeKw));
        }
        if (StringUtils.hasText(status)) {
            w.eq(BusinessApplication::getStatus, status);
        }
        if (StringUtils.hasText(businessTypeCode)) {
            w.eq(BusinessApplication::getBusinessTypeCode, businessTypeCode);
        }
        if (submitUserId != null) {
            w.eq(BusinessApplication::getSubmitUserId, submitUserId);
        }
        if (handlingDepartmentId != null) {
            w.eq(BusinessApplication::getHandlingDepartmentId, handlingDepartmentId);
        }
        w.orderByDesc(BusinessApplication::getCreatedAt);
        return this.page(page, w);
    }

    @Override
    public BusinessApplication getDetail(Long applicationId) {
        return this.getById(applicationId);
    }

    @Override
    public boolean submit(Long applicationId) {
        BusinessApplication app = this.getById(applicationId);
        if (app == null) {
            return false;
        }
        app.setStatus("SUBMITTED");
        app.setCurrentStep("PENDING_APPROVAL");
        app.setSubmittedAt(java.time.LocalDateTime.now());
        return this.updateById(app);
    }
}