package com.example.population.dto;

import com.example.population.util.PhoneValidator;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
@Schema(description = "人口更新入参。注意：主身份凭证类型 + 号码不可修改，请走单独的『户籍核验变更』流程")
public class PersonUpdateDTO {

    @Size(max = 50)
    @Schema(description = "姓名")
    private String name;

    @Schema(description = "性别（GENDER 字典）")
    private String genderCode;

    @PastOrPresent
    @Schema(description = "出生日期")
    private LocalDate birthDate;

    @Schema(description = "民族")
    private String ethnicityCode;

    @Schema(description = "手机号")
    private String phone;

    @Size(max = 255)
    @Schema(description = "联系地址")
    private String contactAddress;

    public void validate() {
        if (phone != null && !phone.isBlank()) {
            PhoneValidator.assertValid(phone);
        }
    }
}
