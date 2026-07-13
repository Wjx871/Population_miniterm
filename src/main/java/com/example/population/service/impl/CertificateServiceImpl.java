package com.example.population.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.population.dto.CertificateCreateDTO;
import com.example.population.dto.CertificateUpdateDTO;
import com.example.population.entity.Certificate;
import com.example.population.entity.Person;
import com.example.population.exception.BizException;
import com.example.population.exception.DuplicateException;
import com.example.population.exception.NotFoundException;
import com.example.population.mapper.CertificateMapper;
import com.example.population.mapper.PersonMapper;
import com.example.population.service.CertificateService;
import com.example.population.util.PageUtil;
import com.example.population.util.SafeLike;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.List;

/**
 * 证件管理实现，覆盖业务流程 §2.2.8。
 *
 * <p>P0 修复：
 * <ul>
 *   <li>同类证件编号唯一性（DB uk_certificate_type_no + 服务层复核）</li>
 *   <li>有效期合法性（valid_until ≥ valid_from，签发日期 ≤ 有效开始）</li>
 *   <li>自动状态判定：valid_until < today → EXPIRED；< today+warnDays → EXPIRING；否则 VALID</li>
 *   <li>定时 + 手动扫描：自动把已过期的非 CANCELLED 证件置 EXPIRED</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CertificateServiceImpl extends ServiceImpl<CertificateMapper, Certificate>
        implements CertificateService {

    /** 即将到期默认提醒天数（与 StatsMapper 保持一致）。 */
    public static final int DEFAULT_WARN_DAYS = 30;

    private final PersonMapper personMapper;

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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Certificate createCertificate(CertificateCreateDTO dto) {
        // 1. 人员存在
        Person person = personMapper.selectById(dto.getPersonId());
        if (person == null) {
            throw new NotFoundException("人口[" + dto.getPersonId() + "]不存在");
        }
        // 2. 编号必填（dto 已有 @NotBlank，但仍兜底）
        if (!StringUtils.hasText(dto.getCertificateNo())) {
            throw new BizException(400, "证件编号不能为空");
        }
        // 3. 同类型同编号唯一性（服务层先查 + DB 兜底）
        Certificate exist = baseMapper.findByTypeAndNoForUpdate(
                dto.getCertificateTypeCode(), dto.getCertificateNo());
        if (exist != null) {
            throw new DuplicateException(
                    "同类型证件[" + dto.getCertificateTypeCode() + "]编号["
                            + dto.getCertificateNo() + "]已存在（id=" + exist.getCertificateId() + "）");
        }
        // 4. 日期合法性
        if (dto.getValidUntil() != null && dto.getValidFrom() != null
                && dto.getValidUntil().isBefore(dto.getValidFrom())) {
            throw new BizException(400, "有效期截止日期不得早于生效日期");
        }
        if (dto.getIssueDate() != null && dto.getValidFrom() != null
                && dto.getValidFrom().isBefore(dto.getIssueDate())) {
            throw new BizException(400, "生效日期不得早于签发日期");
        }
        // 5. 写表
        Certificate entity = new Certificate();
        BeanUtils.copyProperties(dto, entity);
        if (!StringUtils.hasText(entity.getCertificateStatus())) {
            entity.setCertificateStatus(resolveStatus(dto.getValidUntil(), DEFAULT_WARN_DAYS));
        }
        try {
            baseMapper.insert(entity);
        } catch (DuplicateKeyException dup) {
            // DB 唯一约束兜底（uk_certificate_type_no），与上面服务层校验互为冗余防绕过
            throw new DuplicateException(
                    "同类型证件编号重复（DB 唯一约束触发）", dup);
        }
        return entity;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Certificate updateCertificate(Long certificateId, CertificateUpdateDTO dto) {
        Certificate entity = baseMapper.selectById(certificateId);
        if (entity == null) {
            throw new NotFoundException("证件[" + certificateId + "]不存在");
        }
        // 状态为 CANCELLED 不允许修改
        if ("CANCELLED".equalsIgnoreCase(entity.getCertificateStatus())) {
            throw new BizException(409, "证件已注销，不可修改");
        }
        // 若修改了编号，必须重新走同类同号唯一性
        boolean noChanged = dto.getCertificateNo() != null
                && !dto.getCertificateNo().equals(entity.getCertificateNo());
        if (noChanged) {
            String newType = dto.getCertificateTypeCode() != null
                    ? dto.getCertificateTypeCode() : entity.getCertificateTypeCode();
            Certificate dup = baseMapper.findByTypeAndNoForUpdate(newType, dto.getCertificateNo());
            if (dup != null && !dup.getCertificateId().equals(certificateId)) {
                throw new DuplicateException("同类型证件编号已存在（id=" + dup.getCertificateId() + "）");
            }
        }
        // 白名单字段映射
        if (dto.getCertificateTypeCode() != null) entity.setCertificateTypeCode(dto.getCertificateTypeCode());
        if (dto.getCertificateNo() != null) entity.setCertificateNo(dto.getCertificateNo());
        if (dto.getIssueAuthority() != null) entity.setIssueAuthority(dto.getIssueAuthority());
        if (dto.getIssueDate() != null) entity.setIssueDate(dto.getIssueDate());
        if (dto.getValidFrom() != null) entity.setValidFrom(dto.getValidFrom());
        if (dto.getValidUntil() != null) entity.setValidUntil(dto.getValidUntil());
        if (dto.getCertificateStatus() != null) entity.setCertificateStatus(dto.getCertificateStatus());
        // 日期合法性
        if (entity.getValidUntil() != null && entity.getValidFrom() != null
                && entity.getValidUntil().isBefore(entity.getValidFrom())) {
            throw new BizException(400, "有效期截止日期不得早于生效日期");
        }
        if (entity.getIssueDate() != null && entity.getValidFrom() != null
                && entity.getValidFrom().isBefore(entity.getIssueDate())) {
            throw new BizException(400, "生效日期不得早于签发日期");
        }
        // 若未显式提供 certificateStatus，则按 validUntil 自动重算
        if (dto.getCertificateStatus() == null) {
            entity.setCertificateStatus(resolveStatus(entity.getValidUntil(), DEFAULT_WARN_DAYS));
        }
        try {
            baseMapper.updateById(entity);
        } catch (DuplicateKeyException dup) {
            throw new DuplicateException("同类型证件编号重复（DB 唯一约束触发）", dup);
        }
        return entity;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean cancelCertificate(Long certificateId) {
        Certificate entity = baseMapper.selectById(certificateId);
        if (entity == null) {
            throw new NotFoundException("证件[" + certificateId + "]不存在");
        }
        entity.setCertificateStatus("CANCELLED");
        return updateById(entity);
    }

    @Override
    public String resolveStatus(LocalDate validUntil, int warnDays) {
        if (validUntil == null) {
            return "VALID";
        }
        LocalDate today = LocalDate.now();
        if (validUntil.isBefore(today)) {
            return "EXPIRED";
        }
        if (validUntil.isBefore(today.plusDays(Math.max(1, warnDays)))) {
            return "EXPIRING";
        }
        return "VALID";
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int scanExpired() {
        int updated = baseMapper.markExpiredAsOf(LocalDate.now());
        log.info("[certificate-scan] 标记 EXPIRED 数: {}", updated);
        return updated;
    }

    @Override
    public List<Certificate> listExpiringCertificates(int warnDays) {
        if (warnDays <= 0) {
            warnDays = DEFAULT_WARN_DAYS;
        }
        return baseMapper.listExpiringCertificates(warnDays);
    }

    @Override
    public IPage<Certificate> searchByCertNo(String keyword, long current, long size) {
        Page<Certificate> page = PageUtil.clamp(current, size);
        LambdaQueryWrapper<Certificate> w = new LambdaQueryWrapper<>();
        String safe = SafeLike.escape(keyword);
        if (safe == null || safe.isEmpty()) {
            // 空关键字：不返回任何结果（综合查询必须传关键字）
            return page.setRecords(java.util.Collections.emptyList());
        }
        w.and(w2 -> w2.like(Certificate::getCertificateNo, safe)
                .or().like(Certificate::getIssueAuthority, safe));
        w.orderByDesc(Certificate::getIssueDate);
        return this.page(page, w);
    }
}