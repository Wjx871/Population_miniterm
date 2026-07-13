package com.example.population.dto;

import com.example.population.entity.Person;
import com.example.population.util.Masked;
import com.example.population.util.MaskedRule;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 人口响应 VO。
 * <p>
 * 设计文档 §6 / D-04：所有身份证号、手机号、姓名等敏感字段统一脱敏输出；
 * L3 管理员可调用 {@code /api/persons/{id}?unmask=true} 走 {@link com.example.population.util.MaskedSerializer#UNMASK}
 * 跳过脱敏（P0）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "人口响应 VO（敏感字段已脱敏）")
public class PersonVO implements Serializable {

    private Long personId;

    @Masked(MaskedRule.NAME)
    private String name;

    private String genderCode;

    private String identityTypeCode;

    @Masked(MaskedRule.ID_CARD)
    private String identityNo;

    private LocalDate birthDate;

    private String ethnicityCode;

    @Masked(MaskedRule.PHONE)
    private String phone;

    @Masked(MaskedRule.ADDRESS)
    private String contactAddress;

    private String recordStatusCode;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public static PersonVO from(Person p) {
        if (p == null) return null;
        return PersonVO.builder()
                .personId(p.getPersonId())
                .name(p.getName())
                .genderCode(p.getGenderCode())
                .identityTypeCode(p.getIdentityTypeCode())
                .identityNo(p.getIdentityNo())
                .birthDate(p.getBirthDate())
                .ethnicityCode(p.getEthnicityCode())
                .phone(p.getPhone())
                .contactAddress(p.getContactAddress())
                .recordStatusCode(p.getRecordStatusCode())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .build();
    }
}