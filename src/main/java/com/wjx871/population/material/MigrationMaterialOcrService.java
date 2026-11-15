package com.wjx871.population.material;

import com.fasterxml.jackson.databind.JsonNode;
import com.wjx871.population.application.ApplicationService;
import com.wjx871.population.application.ApplicationStatus;
import com.wjx871.population.application.BusinessType;
import com.wjx871.population.common.BusinessException;
import com.wjx871.population.person.IdCardValidator;
import com.wjx871.population.person.idcard.IdCardOcrProperties;
import com.wjx871.population.person.idcard.PaddleOcrHttpClient;
import com.wjx871.population.security.CurrentUserContext;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/** 迁入草稿材料的识别预览。只返回本次核对所需字段。 */
@Service
@RequiredArgsConstructor
public class MigrationMaterialOcrService {

    static final Set<String> IMAGE_TYPES = Set.of("image/jpeg", "image/png");
    static final int MAX_IMAGE_BYTES = 3_000_000;
    static final double MIN_LINE_CONFIDENCE = 0.8;
    private static final List<FieldSpec> HOME_FIELDS = List.of(
            new FieldSpec("HouseholdType", "householdType", "户别"),
            new FieldSpec("HouseholderName", "householderName", "户主姓名"),
            new FieldSpec("HouseholdNum", "householdNo", "户号"),
            new FieldSpec("Address", "registeredAddress", "户主页住址"),
            new FieldSpec("IssueDate", "issueDate", "签发日期"));
    private static final List<FieldSpec> MEMBER_FIELDS = List.of(
            new FieldSpec("Name", "name", "姓名"),
            new FieldSpec("Relationship", "relationship", "与户主关系"),
            new FieldSpec("Sex", "gender", "性别"),
            new FieldSpec("Nation", "ethnicity", "民族"),
            new FieldSpec("Birthday", "birthDate", "出生日期"),
            new FieldSpec("CardNo", "idCard", "身份证号"),
            new FieldSpec("HouseholdNum", "householdNo", "户号"),
            new FieldSpec("OtherAddress", "otherAddress", "本市（县）其他住址"),
            new FieldSpec("Date", "registrationDate", "登记日期"));

    private final ApplicationService applications;
    private final PaddleOcrHttpClient ocr;
    private final IdCardOcrProperties config;
    private final IdCardValidator idCards;

    public Result recognizeHousehold(Long applicationId, String page, MultipartFile file) {
        checkPreview(applicationId, file);
        boolean homepage = switch (page) {
            case "homepage" -> true;
            case "subpage" -> false;
            default -> throw new BusinessException(HttpStatus.BAD_REQUEST, "请选择户主页或成员页");
        };
        try {
            return readHousehold(file.getBytes(), file.getContentType(), page, homepage);
        } catch (IOException e) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "无法读取上传图片");
        } catch (PaddleOcrHttpClient.RecognitionFailure e) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
        }
    }

    public Result recognizeForm(Long applicationId, MultipartFile file) {
        checkPreview(applicationId, file);
        try {
            return readForm(file.getBytes(), file.getContentType());
        } catch (IOException e) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "无法读取上传图片");
        } catch (PaddleOcrHttpClient.RecognitionFailure e) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
        }
    }

    Result readHousehold(byte[] bytes, String contentType, String page, boolean homepage) {
        var response = ocr.recognizeHousehold(bytes, contentType, homepage);
        checkProviderError(response, "户口簿");
        return parseHousehold(page, homepage, response);
    }

    Result readForm(byte[] bytes, String contentType) {
        var response = ocr.recognizeText(bytes, contentType);
        checkProviderError(response, "迁入申请表");
        return MigrationFormFields.extract(response.path("words_result"), idCards);
    }

    void assertDraftOwner(Long applicationId) {
        var application = applications.require(applicationId);
        applications.assertOwner(application, CurrentUserContext.requireUser());
        if (application.getBusinessType() != BusinessType.MIGRATION_IN
                || application.getStatus() != ApplicationStatus.DRAFT) {
            throw new BusinessException(HttpStatus.CONFLICT, "仅迁入草稿支持材料识别和预核对");
        }
    }

    private void checkPreview(Long applicationId, MultipartFile file) {
        assertDraftOwner(applicationId);
        if (file.isEmpty() || file.getContentType() == null || !IMAGE_TYPES.contains(file.getContentType())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "请选择单页 JPG 或 PNG 图片");
        }
        if (file.getSize() > MAX_IMAGE_BYTES) {
            throw new BusinessException(HttpStatus.PAYLOAD_TOO_LARGE, "图片超过识别大小限制，请缩小图片后重试");
        }
        assertConfigured();
    }

    void assertConfigured() {
        if (config.getBaiduApiKey().isBlank() || config.getBaiduSecretKey().isBlank()) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "百度材料识别尚未配置");
        }
    }

    private static void checkProviderError(JsonNode response, String material) {
        int error = response.path("error_code").asInt(0);
        switch (error) {
            case 0 -> { }
            case 6 -> throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "百度应用未开通" + material + "识别权限");
            case 17, 19 -> throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "百度" + material + "识别额度不足");
            case 18 -> throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS, "百度" + material + "识别暂时限流，请稍后再试");
            case 282102, 282103 -> throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "图片未匹配" + material + "模板，请重新选择清晰的单页图片");
            default -> throw new BusinessException(HttpStatus.BAD_GATEWAY, "百度未完成" + material + "识别，请核对图片和接口配置");
        }
    }

    private Result parseHousehold(String page, boolean homepage, JsonNode response) {
        var fields = new ArrayList<Field>();
        var words = response.path("words_result");
        for (FieldSpec spec : homepage ? HOME_FIELDS : MEMBER_FIELDS) {
            var node = words.path(spec.providerKey()).path("words");
            if (node.isTextual() && !node.textValue().isBlank() && node.textValue().length() <= 255) {
                fields.add(new Field(spec.key(), spec.label(), node.textValue().strip(), null, null));
            }
        }
        // 户号两种页型都可能返回，只有户号不足以确认所选页型。
        if (fields.stream().noneMatch(field -> !"householdNo".equals(field.key()))) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "未识别到所选页型的字段，请核对页型和图片清晰度");
        }
        var warnings = new ArrayList<String>();
        requireForReview(fields, homepage ? "householderName" : "name", homepage ? "户主姓名" : "姓名", warnings);
        requireForReview(fields, homepage ? "registeredAddress" : "idCard", homepage ? "户主页住址" : "身份证号", warnings);
        for (Field field : fields) {
            if ("idCard".equals(field.key())) {
                try {
                    idCards.parse(field.value());
                } catch (BusinessException e) {
                    warnings.add("识别到的身份证号未通过校验，请对照原图核对");
                }
            }
        }
        return new Result(page, 1, "BAIDU_AI_PLATFORM", List.copyOf(fields), List.copyOf(warnings));
    }

    private static void requireForReview(List<Field> fields, String key, String label, List<String> warnings) {
        if (fields.stream().noneMatch(field -> key.equals(field.key()))) {
            warnings.add("未识别到" + label + "，请人工核对");
        }
    }

    private record FieldSpec(String providerKey, String key, String label) {}
    public record Location(int left, int top, int width, int height) {}
    public record Field(String key, String label, String value, Location location, Double lineConfidence) {}
    public record Result(String page, int pageNumber, String provider, List<Field> fields, List<String> warnings) {}
}
