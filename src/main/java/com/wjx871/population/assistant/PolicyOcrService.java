package com.wjx871.population.assistant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wjx871.population.common.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;

/** Proxies the separately deployed OCR service. Recognition values are never persisted or returned in full. */
@Service
@RequiredArgsConstructor
public class PolicyOcrService {
    private static final Set<String> IMAGE_TYPES = Set.of("image/jpeg", "image/png");
    private final ObjectMapper objectMapper;
    private final HttpClient client = HttpClient.newHttpClient();
    @Value("${policy.ocr.base-url:http://127.0.0.1:8866}") private String baseUrl;
    @Value("${policy.ocr.timeout-seconds:35}") private int timeoutSeconds;
    @Value("${policy.ocr.max-size-mb:5}") private long maxSizeMb;

    public OcrResponse recognizeIdCard(MultipartFile file) {
        validate(file);
        try {
            String boundary = "----PopulationOcr" + System.nanoTime();
            byte[] body = multipart(boundary, file);
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl.replaceAll("/$", "") + "/recognize/idcard"))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) throw new BusinessException(HttpStatus.BAD_GATEWAY, "证件识别服务暂不可用，请稍后重试");
            JsonNode result = objectMapper.readTree(response.body()).path("result");
            if (result.isMissingNode()) throw new BusinessException(HttpStatus.BAD_GATEWAY, "证件识别服务返回异常");
            return new OcrResponse("身份证正面", mask(result.path("name").asText(null)), maskId(result.path("idCard").asText(null)),
                    maskBirth(result.path("birthDate").asText(null)), result.path("gender").asText(null),
                    mask(result.path("address").asText(null)), result.path("confidence").asDouble(0), true,
                    "识别结果仅供核验，请由用户确认或修正后再用于后续办理；系统不会自动写入人口信息。");
        } catch (BusinessException e) { throw e;
        } catch (Exception e) { throw new BusinessException(HttpStatus.BAD_GATEWAY, "证件识别服务连接失败，请确认 OCR 服务已启动"); }
    }
    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new BusinessException(HttpStatus.BAD_REQUEST, "请上传身份证正面图片");
        String type = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (!IMAGE_TYPES.contains(type)) throw new BusinessException(HttpStatus.BAD_REQUEST, "仅支持 JPG、JPEG 或 PNG 图片");
        if (file.getSize() > maxSizeMb * 1024 * 1024) throw new BusinessException(HttpStatus.BAD_REQUEST, "图片超过允许大小");
    }
    private byte[] multipart(String boundary, MultipartFile file) throws Exception {
        String name = file.getOriginalFilename() == null ? "id-card.jpg" : file.getOriginalFilename().replaceAll("[\\r\\n\"]", "_");
        String head = "--" + boundary + "\r\nContent-Disposition: form-data; name=\"side\"\r\n\r\nfront\r\n--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"" + name + "\"\r\nContent-Type: " + file.getContentType() + "\r\n\r\n";
        byte[] prefix = head.getBytes(java.nio.charset.StandardCharsets.UTF_8), image = file.getBytes(), suffix = ("\r\n--" + boundary + "--\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] out = new byte[prefix.length + image.length + suffix.length]; System.arraycopy(prefix, 0, out, 0, prefix.length); System.arraycopy(image, 0, out, prefix.length, image.length); System.arraycopy(suffix, 0, out, prefix.length + image.length, suffix.length); return out;
    }
    private static String mask(String value) { if (value == null || value.isBlank()) return null; return value.length() == 1 ? "*" : value.charAt(0) + "***"; }
    private static String maskId(String value) { return value == null || value.length() < 8 ? null : value.substring(0, 3) + "***********" + value.substring(value.length() - 4); }
    private static String maskBirth(String value) { return value == null || value.length() < 4 ? null : value.substring(0, 4) + "-**-**"; }
    public record OcrResponse(String documentType, String name, String idCard, String birthDate, String gender, String address, double confidence, boolean requiresConfirmation, String notice) { }
}
