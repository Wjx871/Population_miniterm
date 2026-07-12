package com.example.population.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 数据字典可变更字段白名单。dictId / dictType / dictCode 不可修改。
 */
@Data
@Schema(description = "数据字典更新入参（白名单字段）")
public class DataDictionaryUpdateDTO {

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