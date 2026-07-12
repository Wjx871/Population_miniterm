package com.example.population.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 行政区划可变更字段白名单。regionCode 不可修改。
 */
@Data
@Schema(description = "行政区划更新入参")
public class AdminRegionUpdateDTO {

    @Size(max = 100)
    @Schema(description = "区划名称")
    private String regionName;

    @Pattern(regexp = "^(PROVINCE|CITY|DISTRICT|STREET)$", message = "levelCode 仅允许枚举值")
    @Schema(description = "行政等级")
    private String levelCode;

    @Size(max = 20)
    @Schema(description = "上级区划编码")
    private String parentCode;

    @Schema(description = "是否启用 0/1")
    private Integer enabled;
}