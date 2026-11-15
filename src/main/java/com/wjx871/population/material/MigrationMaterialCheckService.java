package com.wjx871.population.material;

import com.wjx871.population.common.BusinessException;
import com.wjx871.population.material.MigrationMaterialOcrService.Field;
import com.wjx871.population.material.MigrationMaterialOcrService.Location;
import com.wjx871.population.person.IdCardValidator;
import com.wjx871.population.person.idcard.PaddleOcrHttpClient;
import com.wjx871.population.security.SensitiveDataMaskingService;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.Semaphore;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** 比对迁入草稿中同一办理人的身份证、户口簿成员页和申请表。 */
@Service
@RequiredArgsConstructor
public class MigrationMaterialCheckService {
    private final ApplicationMaterialMapper materials;
    private final LocalFileStorageService storage;
    private final MigrationMaterialOcrService previews;
    private final PaddleOcrHttpClient ocr;
    private final IdCardValidator idCards;
    private final SensitiveDataMaskingService masking;
    private final Semaphore slot = new Semaphore(1);

    public Result check(Long applicationId, Selection request) {
        previews.assertDraftOwner(applicationId);
        // 全部材料先校验归属与类型，避免先发送有效文件再发现其他选择越权。
        var selected = List.of(
                select(applicationId, Kind.IDENTITY_PROOF, request.identityMaterialId()),
                select(applicationId, Kind.HOUSEHOLD_BOOK, request.householdMaterialId()),
                select(applicationId, Kind.APPLICATION_FORM, request.formMaterialId()));
        rejectDuplicates(selected);
        if (selected.stream().filter(item -> item.material() != null).count() < 2) {
            return summarize(selected.stream().map(item -> unavailable(item, "至少选择两份材料才能比对")).toList());
        }

        previews.assertConfigured();
        if (!slot.tryAcquire()) {
            throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS, "正在核对其他材料，请稍后再试");
        }
        try {
            // 逐份识别，限制一次检查的内存占用和调用并发。
            return summarize(selected.stream().map(this::recognize).toList());
        } finally {
            slot.release();
        }
    }

    private Selected select(Long applicationId, Kind type, Long materialId) {
        if (materialId == null) return new Selected(type, null);
        var material = materials.selectById(materialId)
                .filter(item -> applicationId.equals(item.getApplicationId()) && type.name().equals(item.getMaterialType()))
                .orElseThrow(() -> new BusinessException(HttpStatus.BAD_REQUEST,
                        "所选材料不属于当前草稿或类型不符，请刷新后重新选择"));
        return new Selected(type, material);
    }

    private static void rejectDuplicates(List<Selected> selected) {
        var hashes = new HashSet<String>();
        for (var item : selected) {
            if (item.material() != null && !hashes.add(item.material().getFileSha256())) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "所选材料包含重复图片，请分别选择对应的原件");
            }
        }
    }

    private ReadDocument recognize(Selected selected) {
        var material = selected.material();
        if (material == null) return unavailable(selected, null);
        String contentType = material.getContentType();
        if (contentType == null || !MigrationMaterialOcrService.IMAGE_TYPES.contains(contentType)) {
            return unavailable(selected, "请使用单页 JPG 或 PNG 图片");
        }
        var bytes = readImage(material);
        if (bytes == null) return unavailable(selected, "材料无法读取或超过识别大小限制，请重新选择");
        try {
            List<Field> fields = switch (selected.type()) {
                case IDENTITY_PROOF -> {
                    var identity = ocr.recognize(bytes, contentType);
                    yield List.of(new Field("name", "姓名", identity.name(), null, identity.confidence()),
                            new Field("idCard", "证件号", identity.idCard(), null, identity.confidence()));
                }
                case HOUSEHOLD_BOOK -> previews.readHousehold(bytes, contentType, "subpage", false).fields();
                case APPLICATION_FORM -> previews.readForm(bytes, contentType).fields();
            };
            return new ReadDocument(selected, value(fields, "name"), value(fields, "idCard"), null);
        } catch (PaddleOcrHttpClient.RecognitionFailure | BusinessException e) {
            return unavailable(selected, e.getMessage());
        }
    }

    private byte[] readImage(ApplicationMaterial material) {
        try (var stream = storage.load(material.getStoredFilename()).getInputStream()) {
            var bytes = stream.readNBytes(MigrationMaterialOcrService.MAX_IMAGE_BYTES + 1);
            if (bytes.length == 0 || bytes.length > MigrationMaterialOcrService.MAX_IMAGE_BYTES
                    || !Objects.equals(material.getFileSize(), (long) bytes.length)
                    || !sha256(bytes).equalsIgnoreCase(material.getFileSha256())) {
                return null;
            }
            return bytes;
        } catch (IOException | BusinessException e) {
            return null;
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static ReadDocument unavailable(Selected selected, String issue) {
        return new ReadDocument(selected, new ReadValue(null, null, issue), new ReadValue(null, null, issue), issue);
    }

    private ReadValue value(List<Field> fields, String key) {
        var field = fields.stream().filter(item -> key.equals(item.key())).findFirst().orElse(null);
        if (field == null || field.value() == null) return new ReadValue(null, null, "字段缺失或有歧义");
        if (field.lineConfidence() != null && field.lineConfidence() < MigrationMaterialOcrService.MIN_LINE_CONFIDENCE) {
            return new ReadValue(null, field.location(), "识别置信度较低");
        }
        String normalized = Normalizer.normalize(field.value(), Normalizer.Form.NFKC).replaceAll("[\\p{Z}\\s]", "");
        if ("idCard".equals(key)) {
            try {
                normalized = idCards.parse(normalized.toUpperCase(Locale.ROOT)).normalized();
            } catch (BusinessException e) {
                return new ReadValue(null, field.location(), "证件号校验未通过");
            }
        } else if (normalized.isBlank() || normalized.length() > 50 || normalized.codePoints().noneMatch(Character::isLetter)) {
            return new ReadValue(null, field.location(), "姓名需人工核对");
        }
        return new ReadValue(normalized, field.location(), null);
    }

    private Result summarize(List<ReadDocument> documents) {
        long selectedCount = documents.stream().filter(document -> document.selected().material() != null).count();
        var nameStatus = compare(selectedCount, documents.stream().map(ReadDocument::name).toList());
        var numberStatus = compare(selectedCount, documents.stream().map(ReadDocument::number).toList());
        Status status;
        if (nameStatus == Status.CONFLICT || numberStatus == Status.CONFLICT) {
            status = Status.CONFLICT;
        } else if (selectedCount < documents.size()) {
            status = Status.MISSING;
        } else if (nameStatus == Status.MATCH && numberStatus == Status.MATCH) {
            status = Status.MATCH;
        } else {
            status = Status.UNCERTAIN;
        }
        return new Result(status, nameStatus, numberStatus, documents.stream().map(this::toEvidence).toList());
    }

    private static Status compare(long selectedCount, List<ReadValue> values) {
        if (selectedCount < 2) return Status.MISSING;
        var usable = values.stream().map(ReadValue::value).filter(Objects::nonNull).toList();
        if (usable.stream().distinct().count() > 1) return Status.CONFLICT;
        return usable.size() == selectedCount ? Status.MATCH : Status.UNCERTAIN;
    }

    private DocumentEvidence toEvidence(ReadDocument document) {
        var selected = document.selected();
        var material = selected.material();
        var name = document.name();
        var number = document.number();
        return new DocumentEvidence(selected.type(), material == null ? null : material.getMaterialId(),
                material == null ? null : masking.filename(material.getMaterialName()), document.issue(),
                new Evidence(masking.auditDetail(name.value()), name.location(), name.issue()),
                new Evidence(masking.identityAlways(number.value()), number.location(), number.issue()));
    }

    public enum Kind { IDENTITY_PROOF, HOUSEHOLD_BOOK, APPLICATION_FORM }
    public enum Status { MATCH, CONFLICT, MISSING, UNCERTAIN }
    public record Selection(Long identityMaterialId, Long householdMaterialId, Long formMaterialId) {}
    public record Evidence(String value, Location location, String issue) {}
    public record DocumentEvidence(Kind materialType, Long materialId, String materialName, String issue,
                                   Evidence name, Evidence idCard) {}
    public record Result(Status status, Status nameStatus, Status idCardStatus, List<DocumentEvidence> documents) {}

    private record Selected(Kind type, ApplicationMaterial material) {}
    private record ReadValue(String value, Location location, String issue) {}
    private record ReadDocument(Selected selected, ReadValue name, ReadValue number, String issue) {}
}
