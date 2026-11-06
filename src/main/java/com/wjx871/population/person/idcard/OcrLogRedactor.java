package com.wjx871.population.person.idcard;

import org.springframework.stereotype.Component;

/** Raw OCR payloads are never persisted: names, addresses and nested provider fields may be sensitive. */
@Component
public class OcrLogRedactor {
    public String redact(String rawJson) {
        return rawJson == null || rawJson.isBlank() ? null : "{\"redacted\":true}";
    }
}
