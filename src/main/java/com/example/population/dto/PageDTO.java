package com.example.population.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "分页请求")
public class PageDTO {

    @Schema(description = "当前页码", example = "1")
    private long current = 1;

    @Schema(description = "每页数量", example = "10")
    private long size = 10;

    @Schema(description = "排序字段")
    private String orderBy;

    @Schema(description = "排序方向: asc, desc")
    private String sortDirection = "desc";
}