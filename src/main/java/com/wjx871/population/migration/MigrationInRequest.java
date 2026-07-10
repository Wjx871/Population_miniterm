package com.wjx871.population.migration;
import jakarta.validation.constraints.*; import java.time.LocalDate;
public record MigrationInRequest(@NotNull Long personId,@NotNull MigrationType migrationType,@Size(max=20) String fromRegionCode,@NotBlank @Size(max=255) String fromAddress,@NotBlank @Size(max=20) String toRegionCode,@NotNull Long toHouseholdId,@NotNull LocalDate inDate,@NotBlank String reason,@NotBlank @Size(max=200) String title,@Size(max=500) String remark,@Size(max=40) String transferBatchNo,Integer version) {}
