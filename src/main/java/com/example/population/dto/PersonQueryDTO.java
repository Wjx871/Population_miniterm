package com.example.population.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "人口查询条件")
public class PersonQueryDTO extends PageDTO {

    @Schema(description = "姓名（模糊）")
    private String name;

    @Schema(description = "证件类型")
    private String identityType;

    @Schema(description = "证件号码（精确）")
    private String identityNo;

    @Schema(description = "性别")
    private String gender;

    @Schema(description = "民族")
    private String ethnicity;

    @Schema(description = "档案状态")
    private String status;

    @Schema(description = "手机号（精确或模糊，模糊可用 138**** 形式）")
    private String phone;

    @Schema(description = "出生日期起")
    private LocalDate birthDateStart;

    @Schema(description = "出生日期止")
    private LocalDate birthDateEnd;

    @Schema(description = "所属区划编码（按当前户籍过滤）")
    private String regionCode;

    @Schema(description = "户籍地址（模糊）")
    private String registeredAddress;

    @Schema(description = "所属户号（按 residence_registration → household 过滤）")
    private String householdNo;
}
