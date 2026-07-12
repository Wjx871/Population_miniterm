package com.example.population.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 审批草稿快照。
 * <p>
 * L2 经办人调业务（如新增人口），业务数据先以本 DTO 的形式塞进 sys_approval_request，
 * 等 L3 审批通过后由 ApprovalGateService 反序列化并真正落地。
 * <p>
 * 业务类型与载荷约定：
 * <ul>
 *   <li>PERSON_CREATE / PERSON_UPDATE</li>
 *   <li>HOUSEHOLD_CREATE / HOUSEHOLD_ESTABLISH</li>
 *   <li>MIGRATION_IN / MIGRATION_OUT</li>
 *   <li>PERMIT_APPLY</li>
 * </ul>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "审批草稿载荷")
public class ApprovalDraftDTO implements Serializable {

    /** 业务类型，便于审批时反序列化。 */
    @Schema(description = "业务类型，如 PERSON_CREATE")
    private String businessType;

    /** 业务主键 ID（如已有，传；新增一般空）。 */
    @Schema(description = "业务主键 ID（新增时为空）")
    private Long businessId;

    /**
     * JSON 字符串载荷。承载原始 DTO（如 PersonCreateDTO）。
     * <p>
     * 注意：本字段是审批前的"虚拟数据"，不能直接 SELECT 出来作为正式数据。
     */
    @Schema(description = "JSON 字符串载荷，承载原业务 DTO")
    private String payloadJson;

    /** 申请人提交的简短理由。 */
    @Schema(description = "申请理由")
    private String applyReason;

    /**
     * 业务申请的 applicationId（可选）。
     * <p>
     * 用于关联前端提前在 POST /api/business-applications 创建的业务申请主单，
     * 配合 application_material 的最低必交材料校验。
     * <p>
     * 一旦传入，审批落地（approve）前会要求该 applicationId 下所有必交材料均已 VERIFIED。
     */
    @Schema(description = "业务申请 ID（用于材料必交校验）")
    private Long applicationId;
}
