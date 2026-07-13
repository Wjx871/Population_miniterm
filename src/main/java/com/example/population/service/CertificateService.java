package com.example.population.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.example.population.dto.CertificateCreateDTO;
import com.example.population.dto.CertificateUpdateDTO;
import com.example.population.entity.Certificate;

import java.time.LocalDate;
import java.util.List;

public interface CertificateService extends IService<Certificate> {

    IPage<Certificate> page(long current, long size, Long personId, String certificateTypeCode, String certificateStatus);

    /**
     * 新增证件：校验同类同号唯一性 + 日期合法性 + 自动判定状态。
     */
    Certificate createCertificate(CertificateCreateDTO dto);

    /**
     * 更新证件：保留 number 不允许直接修改，需走先注销再新增。
     */
    Certificate updateCertificate(Long certificateId, CertificateUpdateDTO dto);

    /**
     * 注销证件：将状态置 CANCELLED。
     */
    boolean cancelCertificate(Long certificateId);

    /**
     * 自动判定状态：根据 valid_until 与当前日期决定 EXPIRING/EXPIRED/VALID。
     */
    String resolveStatus(LocalDate validUntil, int warnDays);

    /**
     * 全表扫描：把已过期非 CANCELLED 的批量置 EXPIRED。返回受影响数。
     */
    int scanExpired();

    /**
     * 即将到期 / 已过期证件清单（warnDays 内）。
     */
    java.util.List<Certificate> listExpiringCertificates(int warnDays);
}