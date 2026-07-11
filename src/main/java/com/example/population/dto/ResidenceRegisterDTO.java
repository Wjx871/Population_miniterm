package com.example.population.dto;

import com.example.population.exception.BizException;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.time.LocalDate;

@Data
@Schema(description = "当前户籍登记入参")
public class ResidenceRegisterDTO {

    @NotNull
    @Schema(description = "人口 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long personId;

    @NotNull
    @Schema(description = "家庭户 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long householdId;

    @NotBlank
    @Pattern(regexp = "^(INITIAL|BIRTH|MIGRATION_IN|RESTORE)$", message = "登记类型必须在 REGISTER_TYPE 字典内")
    @Schema(description = "登记类型（REGISTER_TYPE 字典）", requiredMode = Schema.RequiredMode.REQUIRED)
    private String registerTypeCode;

    @NotNull
    @PastOrPresent
    @Schema(description = "登记日期")
    private LocalDate registerDate;

    @Schema(description = "登记地址，默认按户地址自动填充")
    private String registeredAddress;

    @Schema(description = "所属区划编码，默认按户 region_code 自动填充")
    private String regionCode;

    @NotNull
    @PastOrPresent
    @Schema(description = "生效日期")
    private LocalDate startDate;

    @Schema(description = "来源业务申请单 ID（迁入/出生等登记流程追溯）")
    private Long sourceApplicationId;

    /**
     * 自检：保证 PERSON 一对一时被显式刷新。
     */
    public void validate() {
        if (startDate.isBefore(registerDate)) {
            throw new BizException("生效日期不可早于登记日期");
        }
    }
}
