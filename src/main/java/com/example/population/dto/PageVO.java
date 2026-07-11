package com.example.population.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "分页响应")
public class PageVO<T> {

    @Schema(description = "总记录数")
    private long total;

    @Schema(description = "总页数")
    private long pages;

    @Schema(description = "当前页数据")
    private List<T> records;

    @Schema(description = "当前页码")
    private long current;

    @Schema(description = "每页数量")
    private long size;

    public static <T> PageVO<T> of(long total, long pages, List<T> records, long current, long size) {
        return new PageVO<>(total, pages, records, current, size);
    }
}