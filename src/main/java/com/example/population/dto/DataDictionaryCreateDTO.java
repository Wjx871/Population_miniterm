package com.example.population.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 数据字典新增入参。
 */
@Data
@Schema(description = "数据字典新增入参")
public class DataDictionaryCreateDTO {

    @NotBlank
    @Size(max = 50)
    @Schema(description = "字典类型")
    private String dictType;

    @NotBlank
    @Size(max = 50)
    @Schema(description = "字典编码")
    private String dictCode;

    @NotBlank
    @Size(max = 100)
    @Schema(description = "字典标签")
    private String dictLabel;

    @Schema(description = "排序号")
    private Integer sortNo;

    @Pattern(regexp = "^(ENABLED|DISABLED)$")
    @Size(max = 20)
    @Schema(description = "状态")
    private String status;

    @Size(max = 255)
    @Schema(description = "备注")
    private String remark;
}