package com.wjx871.population.person.idcard;

import com.fasterxml.jackson.databind.JsonNode;
import com.wjx871.population.common.BusinessException;
import com.wjx871.population.person.IdCardValidator;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.Locale;
import java.util.Set;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

/** 身份证正面识别。兼容现有 multipart 子服务和百度智能云直连。 */
@Component
public class PaddleOcrHttpClient {

    private static final URI BAIDU_TOKEN_URL = URI.create("https://aip.baidubce.com/oauth/2.0/token");
    private static final String BAIDU_IDCARD_URL = "https://aip.baidubce.com/rest/2.0/ocr/v1/idcard";
    private static final String BAIDU_HOUSEHOLD_URL = "https://aip.baidubce.com/rest/2.0/ocr/v1/household_register";
    private static final String BAIDU_TEXT_URL = "https://aip.baidubce.com/rest/2.0/ocr/v1/accurate";
    private static final Set<String> LOCAL_BRIDGE_HOSTS =
            Set.of("localhost", "127.0.0.1", "[::1]", "ocr", "host.docker.internal");
    private static final int BAIDU_MAX_ENCODED_BYTES = 4_000_000;

    private final RestTemplate restTemplate;
    private final IdCardOcrProperties properties;
    private final IdCardValidator idCards;
    private String accessToken;
    private Instant refreshTokenAt = Instant.EPOCH;

    public PaddleOcrHttpClient(IdCardOcrProperties properties, IdCardValidator idCards) {
        this.properties = properties;
        this.idCards = idCards;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory() {
            @Override
            protected void prepareConnection(HttpURLConnection connection, String method) throws IOException {
                super.prepareConnection(connection, method);
                connection.setInstanceFollowRedirects(false);
            }
        };
        factory.setConnectTimeout(Math.min(3000, properties.getTimeoutMs()));
        factory.setReadTimeout(properties.getTimeoutMs());
        this.restTemplate = new RestTemplate(factory);
    }

    public IdCardOcrProperties.OcrResult recognize(byte[] fileBytes, String contentType) {
        validateImage(fileBytes, contentType);
        try {
            return properties.useBaiduDirect() ? recognizeBaidu(fileBytes) : recognizeBridge(fileBytes, contentType);
        } catch (RestClientResponseException failure) {
            throw new OcrRequestException("OCR_HTTP_" + failure.getStatusCode().value());
        } catch (ResourceAccessException failure) {
            throw new OcrRequestException("OCR_UNAVAILABLE");
        } catch (RestClientException failure) {
            throw new OcrRequestException("OCR_REQUEST_FAILED");
        }
    }

    private IdCardOcrProperties.OcrResult recognizeBaidu(byte[] fileBytes) {
        var form = new LinkedMultiValueMap<String, String>();
        form.add("id_card_side", "front");
        form.add("detect_direction", "true");
        return readBaiduResult(postBaiduImage(BAIDU_IDCARD_URL, fileBytes, form));
    }

    /** 返回百度户口簿结果，材料服务负责按页型提取所需字段。 */
    public JsonNode recognizeHousehold(byte[] fileBytes, String contentType, boolean homepage) {
        validateImage(fileBytes, contentType);
        var form = new LinkedMultiValueMap<String, String>();
        form.add("household_register_side", homepage ? "homepage" : "subpage");
        try {
            return postBaiduImage(BAIDU_HOUSEHOLD_URL, fileBytes, form);
        } catch (RestClientException e) {
            throw new RecognitionFailure("百度户口簿识别请求失败，请稍后重试");
        }
    }

    public JsonNode recognizeText(byte[] fileBytes, String contentType) {
        validateImage(fileBytes, contentType);
        var form = new LinkedMultiValueMap<String, String>();
        form.add("detect_direction", "false");
        form.add("probability", "true");
        try {
            return postBaiduImage(BAIDU_TEXT_URL, fileBytes, form);
        } catch (RestClientException e) {
            throw new RecognitionFailure("百度申请表识别请求失败，请稍后重试");
        }
    }

    private JsonNode postBaiduImage(String url, byte[] fileBytes, LinkedMultiValueMap<String, String> form) {
        if (properties.getBaiduApiKey().isBlank() || properties.getBaiduSecretKey().isBlank()) {
            throw new RecognitionFailure("百度 OCR 凭证配置不完整，请手工录入");
        }
        String image = Base64.getEncoder().encodeToString(fileBytes);
        int encodedSize = java.net.URLEncoder.encode(image, StandardCharsets.UTF_8).length();
        if (encodedSize > BAIDU_MAX_ENCODED_BYTES) {
            throw new RecognitionFailure("图片编码后超过百度 OCR 限额，请缩小图片后重试");
        }

        form.add("image", image);
        URI endpoint = UriComponentsBuilder.fromUriString(url)
                .queryParam("access_token", "{token}").encode().buildAndExpand(baiduToken()).toUri();
        JsonNode result = post(endpoint, form, MediaType.APPLICATION_FORM_URLENCODED);
        int error = result.path("error_code").asInt(0);
        if (error == 110 || error == 111) {
            expireBaiduToken();
        }
        return result;
    }

    private synchronized String baiduToken() {
        if (accessToken != null && Instant.now().isBefore(refreshTokenAt)) {
            return accessToken;
        }
        var form = new LinkedMultiValueMap<String, String>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", properties.getBaiduApiKey());
        form.add("client_secret", properties.getBaiduSecretKey());
        JsonNode result = post(BAIDU_TOKEN_URL, form, MediaType.APPLICATION_FORM_URLENCODED);
        String token = text(result, "access_token");
        long expiresIn = result.path("expires_in").asLong(0);
        if (token == null || expiresIn <= 0) {
            throw new RecognitionFailure("百度 OCR 鉴权失败，请检查后端凭证");
        }
        accessToken = token;
        refreshTokenAt = Instant.now().plusSeconds(Math.max(0, Math.min(expiresIn, 2_592_000) - 60));
        return token;
    }

    private synchronized void expireBaiduToken() {
        refreshTokenAt = Instant.EPOCH;
    }

    IdCardOcrProperties.OcrResult readBaiduResult(JsonNode root) {
        if (root.path("error_code").asInt(0) != 0) {
            throw new RecognitionFailure("百度 OCR 未完成识别，请检查配额或手工录入");
        }
        String status = text(root, "image_status");
        if (!"normal".equals(status)) {
            throw new RecognitionFailure(switch (status == null ? "" : status) {
                case "reversed_side" -> "请上传带姓名和身份证号的正面";
                case "blurred" -> "图片模糊，请重新拍摄或手工录入";
                case "over_exposure", "over_dark" -> "图片曝光不合适，请重新拍摄或手工录入";
                default -> "未能识别身份证正面，请更换图片或手工录入";
            });
        }
        JsonNode fields = root.path("words_result");
        String name = word(fields, "姓名");
        if (name == null) throw new RecognitionFailure("识别姓名缺失，请核对原图后手工录入");
        return normalize(word(fields, "公民身份号码"), name, word(fields, "出生"),
                word(fields, "性别"), word(fields, "民族"), word(fields, "住址"), null);
    }

    private IdCardOcrProperties.OcrResult recognizeBridge(byte[] fileBytes, String contentType) {
        if (properties.getUrl() == null || properties.getUrl().isBlank()) {
            throw new OcrRequestException("OCR_NOT_CONFIGURED");
        }
        URI endpoint = URI.create(properties.getUrl().strip() + properties.getPath());
        String host = endpoint.getHost();
        if (host == null || endpoint.getUserInfo() != null || endpoint.getFragment() != null
                || !("https".equalsIgnoreCase(endpoint.getScheme())
                || ("http".equalsIgnoreCase(endpoint.getScheme()) && LOCAL_BRIDGE_HOSTS.contains(host.toLowerCase(Locale.ROOT))))) {
            throw new RecognitionFailure("OCR 子服务必须使用 HTTPS 或部署配置中的本地服务地址");
        }
        var multipart = new LinkedMultiValueMap<String, Object>();
        var resource = new ByteArrayResource(fileBytes) {
            @Override
            public String getFilename() {
                return "image/png".equals(contentType) ? "idcard.png" : "idcard.jpg";
            }
        };
        HttpHeaders fileHeaders = new HttpHeaders();
        fileHeaders.setContentType(MediaType.parseMediaType(contentType));
        multipart.add("file", new HttpEntity<>(resource, fileHeaders));
        multipart.add("side", "front");
        JsonNode result = post(endpoint, multipart, MediaType.MULTIPART_FORM_DATA).path("result");
        JsonNode score = result.path("confidence");
        Double confidence = score.isMissingNode() || score.isNull() ? null : score.asDouble(Double.NaN);
        if (confidence != null && (!score.isNumber() || !Double.isFinite(confidence)
                || confidence < 0 || confidence > 1)) {
            throw new OcrRequestException("OCR_INVALID_RESPONSE");
        }
        try {
            return normalize(bridgeText(result, "idCard"), bridgeText(result, "name"), bridgeText(result, "birthDate"),
                    bridgeText(result, "gender"), bridgeText(result, "ethnicity"), bridgeText(result, "address"), confidence);
        } catch (RecognitionFailure failure) {
            throw new OcrRequestException("OCR_INVALID_RESPONSE");
        }
    }

    private static String bridgeText(JsonNode result, String key) {
        JsonNode value = result.path(key);
        if (value.isMissingNode() || value.isNull()) return null;
        if (!value.isTextual()) throw new OcrRequestException("OCR_INVALID_RESPONSE");
        return text(result, key);
    }

    private JsonNode post(URI endpoint, Object body, MediaType type) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(type);
        var response = restTemplate.postForEntity(endpoint, new HttpEntity<>(body, headers), JsonNode.class);
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new RecognitionFailure("OCR 服务没有返回有效结果");
        }
        return response.getBody();
    }

    private IdCardOcrProperties.OcrResult normalize(String idCard, String name, String date, String gender,
                                                    String ethnicity, String address, Double confidence) {
        if (idCard == null || (name != null && name.length() > 50) || (ethnicity != null && ethnicity.length() > 19)
                || (address != null && address.length() > 255)) {
            throw new RecognitionFailure("识别字段缺失或异常，请核对原图后手工录入");
        }
        String normalizedGender = switch (gender == null ? "" : gender.toUpperCase(Locale.ROOT)) {
            case "男", "M" -> "M";
            case "女", "F" -> "F";
            case "" -> null;
            default -> throw new RecognitionFailure("识别性别异常，请核对原图后手工录入");
        };
        try {
            LocalDate birthDate = date == null ? null : LocalDate.parse(date,
                    date.matches("[0-9]{8}") ? DateTimeFormatter.BASIC_ISO_DATE : DateTimeFormatter.ISO_LOCAL_DATE);
            IdCardValidator.Identity identity = idCards.parse(idCard);
            if ((birthDate != null && !birthDate.equals(identity.birthDate()))
                    || (normalizedGender != null && !normalizedGender.equals(identity.gender()))) {
                throw new RecognitionFailure("识别的出生日期或性别与身份证号不一致，请核对原图");
            }
            String normalizedEthnicity = ethnicity == null || ethnicity.endsWith("族") ? ethnicity : ethnicity + "族";
            return new IdCardOcrProperties.OcrResult(identity.normalized(), name, birthDate, normalizedGender,
                    normalizedEthnicity, address, confidence);
        } catch (BusinessException | DateTimeParseException e) {
            throw new RecognitionFailure("识别的身份证号或出生日期无效，请核对原图后手工录入");
        }
    }

    private void validateImage(byte[] data, String type) {
        String format = switch (type == null ? "" : type) {
            case "image/jpeg" -> "JPEG";
            case "image/png" -> "PNG";
            default -> throw new RecognitionFailure("OCR 仅支持 JPG 或 PNG 图片");
        };
        try (var image = new MemoryCacheImageInputStream(new ByteArrayInputStream(data))) {
            var readers = ImageIO.getImageReaders(image);
            if (readers.hasNext()) {
                var reader = readers.next();
                try {
                    reader.setInput(image, true, true);
                    int width = reader.getWidth(0);
                    int height = reader.getHeight(0);
                    if (format.equalsIgnoreCase(reader.getFormatName())
                            && (!properties.useBaiduDirect()
                            || (Math.min(width, height) >= 15 && Math.max(width, height) <= 4096))) {
                        return;
                    }
                } finally {
                    reader.dispose();
                }
            }
        } catch (IOException e) {
            throw new RecognitionFailure("无法读取图片，请重新选择 JPG 或 PNG");
        }
        throw new RecognitionFailure("图片格式或尺寸不符合 OCR 要求");
    }

    private static String word(JsonNode fields, String label) {
        return text(fields.path(label), "words");
    }

    private static String text(JsonNode root, String name) {
        JsonNode value = root.path(name);
        return value.isTextual() && !value.textValue().isBlank() ? value.textValue().strip() : null;
    }

    public static class OcrRequestException extends RuntimeException {
        OcrRequestException(String message) { super(message); }
    }

    public static final class RecognitionFailure extends OcrRequestException {
        RecognitionFailure(String message) { super(message); }
    }
}
