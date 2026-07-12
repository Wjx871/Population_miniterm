package com.example.population.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * 当前户籍登记可变更字段白名单。registrationId / personId 不可修改。
 * <p>
 * 实际上该表不应被通用 PUT 更新；变更应通过"归档+新增"完成。
 */
@Data
@Schema(description = "户籍登记更新入参（白名单字段，业务上请走归档+新增）")
public class ResidenceRegistrationUpdateDTO {

    @Size(max = 20)
    @Schema(description = "登记类型")
    private String registerTypeCode;

    @Schema(description = "登记日期")
    private LocalDate registerDate;

    @Size(max = 255)
    @Schema(description = "登记地址")
    private String registeredAddress;

    @Size(max = 20)
    @Schema(description = "区划编码")
    private String regionCode;

    @Schema(description = "起始日期")
    private LocalDate startDate;
}