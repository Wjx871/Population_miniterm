package com.wjx871.population.reference;import jakarta.validation.constraints.*;
record DictionaryCreateRequest(@NotBlank@Size(max=50)@Pattern(regexp="[A-Z0-9_]+")String dictionaryType,@NotBlank@Size(max=50)@Pattern(regexp="[A-Z0-9_]+")String dictionaryCode,@NotBlank@Size(max=100)String displayName,@NotNull@Min(0)Integer sortNo){}
record DictionaryUpdateRequest(@NotBlank@Size(max=100)String displayName,@NotNull@Min(0)Integer sortNo,@NotNull Integer version){}
