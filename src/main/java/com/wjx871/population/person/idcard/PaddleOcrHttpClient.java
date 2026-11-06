package com.wjx871.population.person.idcard;

import java.time.LocalDate;
import java.util.Map;
import com.wjx871.population.person.IdCardValidator;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

/**
 * PaddleOCR HTTP 客户端。
 * <p>
 * 子服务不可达时抛出 RuntimeException，由调用方写入 ocr_status=FAILED。
 * 期望响应 JSON 形如：
 * <pre>
 * {
 *   "result": {
 *     "idCard": "110101199001011234",
 *     "name": "张三",
 *     "birthDate": "1990-01-01",
 *     "gender": "M",
 *     "ethnicity": "汉",
 *     "address": "北京市...",
 *     "confidence": 0.92
 *   }
 * }
 * </pre>
 */
@Component
public class PaddleOcrHttpClient {

    private final RestTemplate restTemplate;
    private final IdCardOcrProperties properties;

    public PaddleOcrHttpClient(IdCardOcrProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory rf = new SimpleClientHttpRequestFactory();
        rf.setConnectTimeout(properties.getTimeoutMs());
        rf.setReadTimeout(properties.getTimeoutMs());
        this.restTemplate = new RestTemplate(rf);
    }

    public IdCardOcrProperties.OcrResult recognize(byte[] fileBytes, String contentType) {
        if (properties.getUrl() == null || properties.getUrl().isBlank()) {
            throw new IllegalStateException("OCR endpoint is not configured (population.person.idcard.ocr.url is empty)");
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        org.springframework.core.io.ByteArrayResource resource =
                new org.springframework.core.io.ByteArrayResource(fileBytes) {
                    @Override public String getFilename() { return "idcard.jpg"; }
                };
        body.add("file", resource);
        body.add("side", "front");
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        String url = properties.getUrl() + properties.getPath();
        ResponseEntity<Map> response;
        try {
            response = restTemplate.postForEntity(url, new HttpEntity<>(body, headers), Map.class);
        } catch (RestClientException e) {
            if (e instanceof RestClientResponseException responseFailure) {
                throw new OcrRequestException("OCR_HTTP_" + responseFailure.getStatusCode().value());
            }
            if (e instanceof ResourceAccessException) {
                throw new OcrRequestException("OCR_UNAVAILABLE");
            }
            throw new OcrRequestException("OCR_REQUEST_FAILED");
        }
        Map<?, ?> root = response.getBody();
        if (root == null) throw new IllegalStateException("OCR response body is null");
        Object resultNode = root.get("result");
        if (!(resultNode instanceof Map<?, ?> result)) {
            throw new IllegalStateException("OCR response missing 'result' object");
        }
        String idCard = asString(result.get("idCard"));
        try {
            if (!(result.get("idCard") instanceof String)) throw new IllegalArgumentException();
            idCard = new IdCardValidator().parse(idCard).normalized();
        } catch (RuntimeException invalid) {
            throw new OcrRequestException("OCR_INVALID_RESPONSE");
        }
        String name = asString(result.get("name"));
        String address = asString(result.get("address"));
        String gender = asString(result.get("gender"));
        String ethnicity = asString(result.get("ethnicity"));
        String birthRaw = asString(result.get("birthDate"));
        LocalDate birth = null;
        if (birthRaw != null && !birthRaw.isBlank()) {
            try { birth = LocalDate.parse(birthRaw); }
            catch (RuntimeException invalid) { throw new OcrRequestException("OCR_INVALID_RESPONSE"); }
        }
        Double confidence = null;
        Object conf = result.get("confidence");
        if (conf instanceof Number n) confidence = n.doubleValue();
        if (conf != null && (!(conf instanceof Number) || !Double.isFinite(confidence)
                || confidence < 0 || confidence > 1)) throw new OcrRequestException("OCR_INVALID_RESPONSE");

        return new IdCardOcrProperties.OcrResult(idCard, name, birth, gender, ethnicity, address, confidence, root.toString());
    }

    private static String asString(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    public static final class OcrRequestException extends RuntimeException {
        private final String errorCode;

        private OcrRequestException(String errorCode) {
            super(errorCode);
            this.errorCode = errorCode;
        }

        public String errorCode() {
            return errorCode;
        }
    }
}
