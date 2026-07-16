package com.wjx871.population.person.idcard;

import java.time.LocalDate;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Phase 14 / V4_013 身份证 OCR 子服务配置。 */
@ConfigurationProperties(prefix = "population.person.idcard.ocr")
public class IdCardOcrProperties {

    /** 远程 PaddleOCR HTTP 服务地址，例 http://127.0.0.1:8866。留空或 unavailable 时回退 SKIPPED。 */
    private String url = "";

    /** 接口路径，默认 /recognize/idcard。 */
    private String path = "/recognize/idcard";

    /** 引擎版本号，写入 person_idcard_image.ocr_engine_version。 */
    private String engineVersion = "2.7.0.3-onnx";

    /** Provider 标识。 */
    private String provider = "PADDLE_LOCAL";

    /** HTTP 超时毫秒。 */
    private int timeoutMs = 8000;

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    public String getEngineVersion() { return engineVersion; }
    public void setEngineVersion(String v) { this.engineVersion = v; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public int getTimeoutMs() { return timeoutMs; }
    public void setTimeoutMs(int timeoutMs) { this.timeoutMs = timeoutMs; }

    /** OCR 子结果。 */
    public record OcrResult(
            String idCard,
            String name,
            LocalDate birthDate,
            String gender,
            String ethnicity,
            String address,
            Double confidence,
            String rawJson
    ) {}
}
