package com.wjx871.population.reference;
import jakarta.validation.constraints.*;
record AdminRegionCreateRequest(@NotBlank@Size(max=20)String regionCode,@NotBlank@Size(max=100)String regionName,@Size(max=20)String parentCode,@NotNull@Min(1)@Max(5)Integer regionLevel,@NotBlank@Size(max=255)String fullName,@NotNull@Min(0)Integer sortNo){}
record AdminRegionUpdateRequest(@NotBlank@Size(max=100)String regionName,@Size(max=20)String parentCode,@NotNull@Min(1)@Max(5)Integer regionLevel,@NotBlank@Size(max=255)String fullName,@NotNull@Min(0)Integer sortNo,@NotNull Integer version){}
record VersionRequest(@NotNull Integer version){}
