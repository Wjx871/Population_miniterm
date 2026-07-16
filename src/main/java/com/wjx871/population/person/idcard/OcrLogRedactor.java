package com.wjx871.population.person.idcard;

import com.wjx871.population.security.SensitiveDataMaskingService;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 把 OCR 原始 JSON 中包含的身份证号做脱敏，写入 ocr_raw_json。
 * 仅由 PersonIdCardImageService 在 OCR 成功时调用。
 */
@Component
public class OcrLogRedactor {

    private final SensitiveDataMaskingService masking;

    public OcrLogRedactor(SensitiveDataMaskingService masking) {
        this.masking = masking;
    }

    public String redact(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) return null;
        try {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("idCard", masking.identity(extract(rawJson, "idCard")));
            out.put("name", extract(rawJson, "name"));
            out.put("address", extract(rawJson, "address"));
            out.put("gender", extract(rawJson, "gender"));
            out.put("ethnicity", extract(rawJson, "ethnicity"));
            out.put("birthDate", extract(rawJson, "birthDate"));
            out.put("confidence", extract(rawJson, "confidence"));
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(out);
        } catch (Exception e) {
            return "{\"idCard\":\"***\"}";
        }
    }

    private String extract(String json, String key) {
        int i = json.indexOf("\"" + key + "\"");
        if (i < 0) return null;
        int colon = json.indexOf(':', i);
        if (colon < 0) return null;
        int q1 = json.indexOf('"', colon);
        if (q1 < 0) return null;
        int q2 = json.indexOf('"', q1 + 1);
        if (q2 < 0) return null;
        return json.substring(q1 + 1, q2);
    }
}
