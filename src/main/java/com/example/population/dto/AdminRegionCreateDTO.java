package com.example.population.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 行政区划新增入参。regionCode 一经创建不可修改。
 */
@Data
@Schema(description = "行政区划新增入参")
public class AdminRegionCreateDTO {

    @NotBlank
    @Pattern(regexp = "^[0-9]{6,12}$", message = "regionCode 仅允许 6-12 位数字")
    @Schema(description = "区划编码")
    private String regionCode;

    @NotBlank
    @Size(max = 100)
    @Schema(description = "区划名称")
    private String regionName;

    @NotBlank
    @Pattern(regexp = "^(PROVINCE|CITY|DISTRICT|STREET)$")
    @Schema(description = "行政等级")
    private String levelCode;

    @Size(max = 20)
    @Schema(description = "上级区划编码")
    private String parentCode;
}