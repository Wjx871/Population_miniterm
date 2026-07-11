package com.wjx871.population.floating;
import jakarta.validation.constraints.*;import java.time.LocalDate;
public record PermitApplicationRequest(Long floatingId,String residenceBasisCode,@NotBlank String title,@NotBlank String reason,String remark,LocalDate requestedValidFrom,LocalDate requestedValidUntil,Integer version){}
