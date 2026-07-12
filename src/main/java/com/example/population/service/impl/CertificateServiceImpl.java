package com.example.population.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.population.entity.Certificate;
import com.example.population.mapper.CertificateMapper;
import com.example.population.service.CertificateService;
import com.example.population.util.PageUtil;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class CertificateServiceImpl extends ServiceImpl<CertificateMapper, Certificate> implements CertificateService {

    @Override
    public IPage<Certificate> page(long current, long size, Long personId,
                                   String certificateTypeCode, String certificateStatus) {
        Page<Certificate> page = PageUtil.clamp(current, size);
        LambdaQueryWrapper<Certificate> w = new LambdaQueryWrapper<>();
        if (personId != null) {
            w.eq(Certificate::getPersonId, personId);
        }
        if (StringUtils.hasText(certificateTypeCode)) {
            w.eq(Certificate::getCertificateTypeCode, certificateTypeCode);
        }
        if (StringUtils.hasText(certificateStatus)) {
            w.eq(Certificate::getCertificateStatus, certificateStatus);
        }
        w.orderByDesc(Certificate::getCreatedAt);
        return this.page(page, w);
    }
}