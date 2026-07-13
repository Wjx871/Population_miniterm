package com.example.population.dto;

import com.example.population.entity.Certificate;
import com.example.population.entity.FloatingPopulation;
import com.example.population.entity.Household;
import com.example.population.entity.KeyPopulation;
import com.example.population.entity.MigrationIn;
import com.example.population.entity.MigrationOut;
import com.example.population.entity.Person;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * §2.2.9 综合查询聚合结果。
 *
 * <p>按业务实体分桶返回，前端可单独渲染"人员 / 户口 / 证件 / 迁入 / 迁出 / 流动人口 / 重点人口" 7 个分块。
 * 敏感字段（身份证号、手机号、证件号）走全局 {@link com.example.population.util.MaskedSerializer}：默认脱敏，
 * 仅 L3 角色且显式传 {@code ?unmask=true} 时输出原文。</p>
 *
 * <p>注：每个 bucket 的 {@code limited} 表示已截断到 {@code limitPerSource} 上限，
 * 前端若发现 {@code limited && total > limitPerSource} 可提示用户缩小关键字或跳转到该业务的分页查询。</p>
 */
@Data
@Schema(description = "综合查询聚合结果")
public class SearchResultDTO {

    @Schema(description = "输入关键字（已做 trim/escape）")
    private String keyword;

    @Schema(description = "本查询结果是否对每个 bucket 都做了 limit 截断")
    private boolean limited;

    @Schema(description = "人员匹配（按姓名/身份证号 like）")
    private List<Person> persons;

    @Schema(description = "人员匹配数（截断前 total）")
    private long personTotal;

    @Schema(description = "户口匹配（户号/户籍地址 like）")
    private List<Household> households;

    @Schema(description = "户口匹配数")
    private long householdTotal;

    @Schema(description = "证件匹配（证件编号/签发机关 like）")
    private List<Certificate> certificates;

    @Schema(description = "证件匹配数")
    private long certificateTotal;

    @Schema(description = "流动人口匹配")
    private List<FloatingPopulation> floatingPopulation;

    @Schema(description = "流动人口匹配数")
    private long floatingTotal;

    @Schema(description = "重点人口匹配（重点类型/原因备注 like）")
    private List<KeyPopulation> keyPopulation;

    @Schema(description = "重点人口匹配数")
    private long keyTotal;

    @Schema(description = "迁入记录匹配（迁入地/批次号 like）")
    private List<MigrationIn> migrationIn;

    @Schema(description = "迁入记录匹配数")
    private long migrationInTotal;

    @Schema(description = "迁出记录匹配（迁往地/批次号 like）")
    private List<MigrationOut> migrationOut;

    @Schema(description = "迁出记录匹配数")
    private long migrationOutTotal;
}
