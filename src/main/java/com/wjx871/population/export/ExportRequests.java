package com.wjx871.population.export;import jakarta.validation.constraints.*;import java.util.*;
record NormalExportRequest(@NotBlank String module,Map<String,Object> filters,@NotEmpty List<String> fields){}
record SensitiveExportRequest(@NotBlank String module,Map<String,Object> filters,@NotEmpty List<String> fields,@NotBlank @Size(max=500)String reason,@NotNull @Min(1)Integer expectedRowLimit,@NotBlank String title,String remark){}
record ExportVersionRequest(@NotNull Integer version){}
