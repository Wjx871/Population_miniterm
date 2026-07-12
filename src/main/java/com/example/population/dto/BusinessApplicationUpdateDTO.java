package com.example.population.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 业务申请可变更字段白名单。applicationId / applicationNo / submitUserId / submittedAt / createdAt / updatedAt / completedAt 不可修改。
 * <p>
 * 实际场景下，业务申请一旦进入 SUBMITTED 状态应不可改；此处保留 PUT 仅用于草稿期纠错。
 */
@Data
@Schema(description = "业务申请更新入参（白名单字段）")
public class BusinessApplicationUpdateDTO {

    @Size(max = 50)
    @Schema(description = "业务类型编码")
    private String businessTypeCode;

    @Size(max = 50)
    @Schema(description = "申请人姓名")
    private String applicantName;

    @Pattern(regexp = "^(ID_CARD|PASSPORT|MILITARY|OTHER)$", message = "applicantIdentityType 仅允许枚举值")
    @Schema(description = "申请人证件类型")
    private String applicantIdentityType;

    @Size(max = 30)
    @Schema(description = "申请人证件号")
    private String applicantIdentityNo;

    @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "手机号格式不正确")
    @Schema(description = "申请人电话")
    private String applicantPhone;

    @Schema(description = "目标人口 ID")
    private Long targetPersonId;

    @Schema(description = "目标户 ID")
    private Long targetHouseholdId;

    @Schema(description = "经办部门 ID")
    private Long handlingDepartmentId;

    @Pattern(regexp = "^(DRAFT|SUBMITTED|PENDING_APPROVAL|APPROVED|REJECTED|CANCELLED)$")
    @Schema(description = "状态")
    private String status;

    @Size(max = 50)
    @Schema(description = "当前步骤")
    private String currentStep;
}