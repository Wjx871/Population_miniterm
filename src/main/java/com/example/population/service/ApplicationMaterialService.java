package com.example.population.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.population.entity.ApplicationMaterial;

import java.util.List;

public interface ApplicationMaterialService extends IService<ApplicationMaterial> {

    List<ApplicationMaterial> listByApplication(Long applicationId);

    boolean verify(Long materialId, Long verifierId, boolean passed);

    /**
     * 按业务类别强制核验：要求 applicationId 对应的申请下，所有"该业务必需的"材料均处于 VERIFIED 状态。
     * <p>
     * 业务必交规则来自《数据库设计v4.0_Cursor详细说明.md》第 7 章：
     * <ul>
     *   <li>HOUSEHOLD_ESTABLISH：身份证明（IDENTITY_DOC） + 户籍/住所（HOUSEHOLD_BOOKLET 或 RESIDENCE_PROOF）</li>
     *   <li>PERSON_REGISTER：身份证明（IDENTITY_DOC）</li>
     *   <li>MIGRATION_IN_*：身份证明 + 迁移或准迁证明（MIGRATION_CERT）</li>
     *   <li>MIGRATION_OUT_*：身份证明 + 迁移或准迁证明</li>
     * </ul>
     * 任一必要项缺失或核验未通过时抛出 BizException(code=400) 以阻止业务落表。
     *
     * @param applicationId 申请主单 ID
     * @param businessType  业务类型编码（BUSINESS_TYPE 字典）
     */
    void assertRequiredVerified(Long applicationId, String businessType);
}