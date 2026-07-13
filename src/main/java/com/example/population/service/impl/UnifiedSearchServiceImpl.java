package com.example.population.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.example.population.dto.SearchResultDTO;
import com.example.population.entity.Certificate;
import com.example.population.entity.FloatingPopulation;
import com.example.population.entity.Household;
import com.example.population.entity.KeyPopulation;
import com.example.population.entity.MigrationIn;
import com.example.population.entity.MigrationOut;
import com.example.population.entity.Person;
import com.example.population.exception.BizException;
import com.example.population.service.CertificateService;
import com.example.population.service.FloatingPopulationService;
import com.example.population.service.HouseholdService;
import com.example.population.service.KeyPopulationService;
import com.example.population.service.MigrationInService;
import com.example.population.service.MigrationOutService;
import com.example.population.service.PersonService;
import com.example.population.service.UnifiedSearchService;
import com.example.population.util.SafeLike;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;

/**
 * 综合查询实现。
 *
 * <p>对应业务流程 §2.2.9 "用户进入综合查询……系统根据查询条件动态组合检索条件……系统从相关数据表中查询有效数据"。
 * 设计要点：
 * <ul>
 *   <li>keyword 必填，且走 {@code SafeLike.escape} 转义（%_\\），避免 LIKE 通配符 DoS</li>
 *   <li>每个 bucket 通过既有 {@code page(...)} 方法查询，自动复用模块的
 *       {@code @DataScope} 过滤器（设计文档 §6：禁止前端绕过权限）</li>
 *   <li>最大单源 50 条；超出时通过 {@code total} 字段告诉前端</li>
 *   <li>对 {@code Person.VO} 的脱敏由全局 {@code MaskedSerializer} 处理，不在本 service 关心</li>
 * </ul>
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UnifiedSearchServiceImpl implements UnifiedSearchService {

    /** 单源硬上限：综合查询单次最多返回的单实体条数，防止 DoS。 */
    public static final int HARD_LIMIT_PER_SOURCE = 50;

    private final PersonService personService;
    private final HouseholdService householdService;
    private final CertificateService certificateService;
    private final FloatingPopulationService floatingPopulationService;
    private final KeyPopulationService keyPopulationService;
    private final MigrationInService migrationInService;
    private final MigrationOutService migrationOutService;

    @Override
    public SearchResultDTO unifiedSearch(String keyword, int limitPerSource) {
        if (!StringUtils.hasText(keyword)) {
            throw new BizException(400, "综合查询关键字不能为空");
        }
        String safe = SafeLike.escape(keyword);
        if (safe == null || safe.isEmpty()) {
            // 超长或全空格的输入统一拒绝
            throw new BizException(400, "综合查询关键字非法或过长（最长 64 字符）");
        }

        int limit = limitPerSource <= 0 ? 10 : Math.min(limitPerSource, HARD_LIMIT_PER_SOURCE);
        long size = limit;

        SearchResultDTO out = new SearchResultDTO();
        out.setKeyword(safe);

        // 1) 人员 - 走现有 findByName（按姓名 like，limit 50，安全 LIKE）
        List<Person> persons = personService.findByName(safe);
        if (persons != null && persons.size() > limit) {
            out.setPersons(persons.subList(0, limit));
            out.setPersonTotal(persons.size());
            out.setLimited(true);
        } else {
            out.setPersons(persons == null ? Collections.emptyList() : persons);
            out.setPersonTotal(persons == null ? 0 : persons.size());
        }

        // 2) 户口 - 走 page(keyword=null...)。HouseholdService.page 已支持 like(户号/地址)
        IPage<Household> hhPage = householdService.page(1L, size, safe, null, null);
        List<Household> households = hhPage.getRecords();
        out.setHouseholds(households);
        out.setHouseholdTotal(hhPage.getTotal());
        if (hhPage.getTotal() > size) out.setLimited(true);

        // 3) 证件 - 用新增的 searchByCertNo；不重写 SQL
        IPage<Certificate> certPage = certificateService.searchByCertNo(safe, 1L, size);
        out.setCertificates(certPage.getRecords());
        out.setCertificateTotal(certPage.getTotal());
        if (certPage.getTotal() > size) out.setLimited(true);

        // 4) 流动人口 - 走 page(keyword)
        IPage<FloatingPopulation> fpPage = floatingPopulationService.page(1L, size, safe, null, null, null);
        out.setFloatingPopulation(fpPage.getRecords());
        out.setFloatingTotal(fpPage.getTotal());
        if (fpPage.getTotal() > size) out.setLimited(true);

        // 5) 重点人口 - 走 page(keyword)
        IPage<KeyPopulation> keyPage = keyPopulationService.page(1L, size, safe, null, null, null, null);
        out.setKeyPopulation(keyPage.getRecords());
        out.setKeyTotal(keyPage.getTotal());
        if (keyPage.getTotal() > size) out.setLimited(true);

        // 6) 迁入 - 走 page(keyword)
        IPage<MigrationIn> miPage = migrationInService.page(1L, size, safe, null, null, null, null);
        out.setMigrationIn(miPage.getRecords());
        out.setMigrationInTotal(miPage.getTotal());
        if (miPage.getTotal() > size) out.setLimited(true);

        // 7) 迁出 - 走 page(keyword)
        IPage<MigrationOut> moPage = migrationOutService.page(1L, size, safe, null, null, null, null);
        out.setMigrationOut(moPage.getRecords());
        out.setMigrationOutTotal(moPage.getTotal());
        if (moPage.getTotal() > size) out.setLimited(true);

        log.info("unifiedSearch: keyword={}, persons={}, households={}, certs={}, floating={}, key={}, mi={}, mo={}",
                safe,
                out.getPersonTotal(), out.getHouseholdTotal(), out.getCertificateTotal(),
                out.getFloatingTotal(), out.getKeyTotal(),
                out.getMigrationInTotal(), out.getMigrationOutTotal());

        return out;
    }
}
