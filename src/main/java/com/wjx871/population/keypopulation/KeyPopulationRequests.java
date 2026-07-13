package com.wjx871.population.keypopulation;import jakarta.validation.constraints.*;import java.time.LocalDate;
record RegisterApplicationRequest(@NotNull Long personId,@NotBlank@Size(max=50)String populationType,@NotNull AttentionLevel attentionLevel,@NotBlank@Size(max=500)String registerReason,@NotNull@PastOrPresent LocalDate registerDate,Long responsibleDepartmentId,Long responsibleUserId,@NotBlank@Size(max=200)String title,@Size(max=500)String remark){}
record ReleaseApplicationRequest(@NotBlank@Size(max=500)String releaseReason,@NotNull@PastOrPresent LocalDate releaseDate,@NotBlank@Size(max=200)String title,@Size(max=500)String remark){}
record KeyExecuteRequest(@NotNull Integer version){}
