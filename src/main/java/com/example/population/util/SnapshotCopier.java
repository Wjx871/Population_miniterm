package com.example.population.util;

import com.example.population.entity.Household;
import com.example.population.entity.ResidenceArchive;
import com.example.population.entity.ResidenceRegistration;

import java.time.LocalDate;

/**
 * 户籍快照拷贝工具：将 residence_registration 的核心字段拷贝到 residence_archive。
 *
 * 所有快照字段在事务边界内生成，确保归档数据"原样不可变"。
 * 维护该类时，必须与 DDL 中 _snapshot 字段列表保持一致。
 */
public final class SnapshotCopier {

    private SnapshotCopier() {}

    public static ResidenceArchive fromRegistration(ResidenceRegistration src,
                                                   String personName,
                                                   String identityTypeCode,
                                                   String identityNo,
                                                   String archiveTypeCode,
                                                   String archiveReasonCode,
                                                   LocalDate archiveDate,
                                                   Long operatorId,
                                                   Long sourceApplicationId,
                                                   Household householdSnapshot) {
        if (src == null) {
            return null;
        }
        ResidenceArchive arc = new ResidenceArchive();
        arc.setOriginalRegistrationId(src.getRegistrationId());
        arc.setPersonId(src.getPersonId());
        arc.setHouseholdId(src.getHouseholdId());
        arc.setArchiveTypeCode(archiveTypeCode);
        arc.setArchiveDate(archiveDate);
        arc.setArchiveReasonCode(archiveReasonCode);

        arc.setPersonNameSnapshot(personName);
        arc.setIdentityTypeSnapshot(identityTypeCode);  // 由调用方注入
        arc.setIdentityNoSnapshot(identityNo);          // 由调用方注入

        if (householdSnapshot != null) {
            arc.setHouseholdNoSnapshot(householdSnapshot.getHouseholdNo());
        }
        arc.setRegisteredAddressSnapshot(src.getRegisteredAddress());
        arc.setRegionCodeSnapshot(src.getRegionCode());
        arc.setRegisterTypeSnapshot(src.getRegisterTypeCode());
        arc.setRegisterDateSnapshot(src.getRegisterDate());
        arc.setStartDateSnapshot(src.getStartDate());
        arc.setEndDateSnapshot(archiveDate);

        arc.setOriginalStatus("ACTIVE");
        arc.setArchiveOperatorId(operatorId);
        arc.setSourceApplicationId(sourceApplicationId);
        return arc;
    }
}
