package com.example.population.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.example.population.entity.Certificate;

public interface CertificateService extends IService<Certificate> {

    IPage<Certificate> page(long current, long size, Long personId, String certificateTypeCode, String certificateStatus);
}