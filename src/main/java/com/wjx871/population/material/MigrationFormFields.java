package com.wjx871.population.material;

import com.fasterxml.jackson.databind.JsonNode;
import com.wjx871.population.common.BusinessException;
import com.wjx871.population.material.MigrationMaterialOcrService.Field;
import com.wjx871.population.material.MigrationMaterialOcrService.Location;
import com.wjx871.population.material.MigrationMaterialOcrService.Result;
import com.wjx871.population.person.IdCardValidator;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;

/** 从单页迁入申请表中提取有明确标签的字段及所在文字行。 */
final class MigrationFormFields {
    private static final Pattern LABEL = Pattern.compile(
            "^\\s*(申请人姓名|申请人|姓名|公民身份号码|身份证号码|身份证号|迁出地地址|迁出地址|迁出地"
                    + "|迁入地地址|迁入地址|迁入地|申请日期|申请时间|迁移原因|申请事由|迁入原因)"
                    + "(?:\\s*[:：]\\s*|\\s+|$)(.*)$");

    private MigrationFormFields() {}

    static Result extract(JsonNode results, IdCardValidator idCards) {
        var lines = new ArrayList<Line>();
        if (results.isArray()) {
            for (JsonNode row : results) {
                var text = row.path("words");
                if (!text.isTextual() || text.textValue().isBlank() || text.textValue().length() > 512) continue;
                var score = row.path("probability").path("average");
                Double confidence = score.isNumber() && score.asDouble() >= 0 && score.asDouble() <= 1
                        ? score.asDouble() : null;
                lines.add(new Line(text.textValue().strip(), location(row.path("location")), confidence));
            }
        }
        var candidates = new LinkedHashMap<String, Map<String, Field>>();
        for (Line line : lines) {
            var match = LABEL.matcher(line.text());
            if (!match.matches()) continue;
            var spec = spec(match.group(1));
            var value = match.group(2).strip();
            var evidence = line;
            if (value.isEmpty()) {
                evidence = singleValueToRight(line, lines);
                if (evidence == null) continue;
                value = evidence.text();
            }
            if (value.length() > 255 || value.matches("[_＿—–-]+")) continue;
            if (spec.key().equals("idCard")) {
                value = value.replaceAll("[\\s　]", "").toUpperCase(java.util.Locale.ROOT);
            }
            candidates.computeIfAbsent(spec.key(), ignored -> new LinkedHashMap<>())
                    .putIfAbsent(value, new Field(spec.key(), spec.label(), value, evidence.location(),
                            evidence.confidence()));
        }
        if (candidates.isEmpty()) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "未识别到迁入申请表的字段，请选择文字清晰、方向正确的单页图片");
        }
        var warnings = new ArrayList<String>();
        var fields = new ArrayList<Field>();
        for (var values : candidates.values()) {
            var first = values.values().iterator().next();
            if (values.size() > 1) {
                warnings.add("同一字段出现不同的" + first.label() + "，请以原图为准");
                continue;
            }
            fields.add(first);
            if (first.lineConfidence() != null && first.lineConfidence() < MigrationMaterialOcrService.MIN_LINE_CONFIDENCE) {
                warnings.add(first.label() + "所在文字行的识别置信度较低，请核对");
            }
            if (first.key().equals("idCard")) {
                try {
                    idCards.parse(first.value());
                } catch (BusinessException e) {
                    warnings.add("识别到的身份证号未通过校验，请核对原图");
                }
            }
        }
        for (String label : List.of("申请人姓名", "身份证号", "迁出地", "迁入地", "申请日期", "迁移原因")) {
            if (fields.stream().noneMatch(field -> field.label().equals(label))) {
                warnings.add(label + "需人工核对");
            }
        }
        return new Result("applicationForm", 1, "BAIDU_AI_PLATFORM", List.copyOf(fields), List.copyOf(warnings));
    }

    // 标签和值被 OCR 拆开时，只接受右侧同一行且唯一的内容。
    private static Line singleValueToRight(Line label, List<Line> lines) {
        var box = label.location();
        if (box == null) return null;
        var sameRow = lines.stream().filter(line -> line.location() != null)
                .filter(line -> {
                    var candidate = line.location();
                    int right = box.left() + box.width();
                    int overlap = Math.min(box.top() + box.height(), candidate.top() + candidate.height())
                            - Math.max(box.top(), candidate.top());
                    return candidate.left() >= right && candidate.left() - right <= box.height() * 8
                            && overlap * 2 >= Math.min(box.height(), candidate.height());
                }).toList();
        int nextLabel = sameRow.stream().filter(line -> LABEL.matcher(line.text()).matches())
                .mapToInt(line -> line.location().left()).min().orElse(Integer.MAX_VALUE);
        var values = sameRow.stream().filter(line -> line.location().left() < nextLabel).toList();
        return values.size() == 1 ? values.get(0) : null;
    }

    private static Location location(JsonNode position) {
        int left = position.path("left").asInt(-1);
        int top = position.path("top").asInt(-1);
        int width = position.path("width").asInt(-1);
        int height = position.path("height").asInt(-1);
        if (left < 0 || top < 0 || width <= 0 || height <= 0
                || (long) left + width > 4096 || (long) top + height > 4096) return null;
        return new Location(left, top, width, height);
    }

    private static Spec spec(String label) {
        return switch (label) {
            case "申请人姓名", "申请人", "姓名" -> new Spec("name", "申请人姓名");
            case "公民身份号码", "身份证号码", "身份证号" -> new Spec("idCard", "身份证号");
            case "迁出地地址", "迁出地址", "迁出地" -> new Spec("fromAddress", "迁出地");
            case "迁入地地址", "迁入地址", "迁入地" -> new Spec("toAddress", "迁入地");
            case "申请日期", "申请时间" -> new Spec("applicationDate", "申请日期");
            default -> new Spec("reason", "迁移原因");
        };
    }

    private record Line(String text, Location location, Double confidence) {}
    private record Spec(String key, String label) {}
}
