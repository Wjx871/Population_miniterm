package com.wjx871.population.migration;
import jakarta.validation.constraints.*; import java.time.LocalDate;
public record MigrationOutRequest(@NotNull Long personId,@NotNull MigrationType migrationType,@Size(max=20) String toRegionCode,@NotBlank @Size(max=255) String toAddress,@NotNull LocalDate outDate,@NotBlank String reason,Long newHeadPersonId,@NotBlank @Size(max=200) String title,@Size(max=500) String remark,Integer version) {}
