package com.example.population.dto;

import com.example.population.util.IdCardValidator;
import com.example.population.util.PhoneValidator;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
@Schema(description = "人口登记 / 新增入参")
public class PersonCreateDTO {

    /**
     * 关联的业务申请主单 ID（必填）。
     * <p>
     * 调用方需先 POST /api/business-applications 创建申请草稿，
     * 再 POST /api/application-materials 上传身份证明等材料、由核验岗材料 VERIFIED，
     * 最后携带此 applicationId 调 POST /api/persons（新增人口）。
     */
    @NotNull(message = "业务申请 ID（applicationId）不能为空；请先创建业务申请并上传身份证明")
    @Schema(description = "业务申请主单 ID（必填，关联 business_application.application_id）",
            example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long applicationId;

    @NotBlank
    @Size(max = 50)
    @Schema(description = "姓名", example = "张三", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @NotBlank
    @Pattern(regexp = "^(MALE|FEMALE|UNKNOWN)$", message = "性别必须为 MALE/FEMALE/UNKNOWN")
    @Schema(description = "性别（GENDER 字典）", example = "MALE", requiredMode = Schema.RequiredMode.REQUIRED)
    private String genderCode;

    @NotBlank
    @Pattern(regexp = "^(ID_CARD|PASSPORT|BIRTH_CERT|OTHER)$", message = "证件类型必须在 IDENTITY_TYPE 字典内")
    @Schema(description = "主身份凭证类型", example = "ID_CARD", requiredMode = Schema.RequiredMode.REQUIRED)
    private String identityTypeCode;

    @NotBlank
    @Schema(description = "主身份凭证号码（身份证由服务层走 IdCardValidator 校验）",
            example = "110101199001011234", requiredMode = Schema.RequiredMode.REQUIRED)
    private String identityNo;

    @PastOrPresent
    @Schema(description = "出生日期")
    private LocalDate birthDate;

    @Pattern(regexp = "^(HAN|OTHER)$", message = "民族必须在 ETHNICITY 字典内")
    @Schema(description = "民族（ETHNICITY 字典）", example = "HAN")
    private String ethnicityCode;

    @Schema(description = "手机号（11 位 1[3-9] 开头）", example = "13800138000")
    private String phone;

    @Size(max = 255)
    @Schema(description = "联系地址")
    private String contactAddress;

    /**
     * 入参级 fail-fast 校验（在 Controller 调用前或 Service 入口调用）。
     */
    public void validate() {
        if ("ID_CARD".equals(identityTypeCode)) {
            IdCardValidator.assertValid(identityNo);
        }
        if (phone != null && !phone.isBlank()) {
            PhoneValidator.assertValid(phone);
        }
    }
}
