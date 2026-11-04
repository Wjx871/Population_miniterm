package com.wjx871.population.ai.precheck;

import com.wjx871.population.common.BusinessException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.text.Normalizer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@ConditionalOnProperty(prefix = "population.ai.precheck", name = "enabled", havingValue = "true")
public class AiPrivateFileStorageService {
    private static final Set<String> ALLOWED_TYPES = Set.of("application/pdf", "image/jpeg", "image/png");
    private final Path root;
    private final Path tempDirectory;
    private final Path objectDirectory;
    private final AiPrecheckProperties properties;

    public AiPrivateFileStorageService(AiPrecheckProperties properties) {
        this.properties = properties;
        properties.validate();
        this.root = properties.getStorageDir().toAbsolutePath().normalize();
        Path webRoot = Path.of("src", "main", "resources", "static").toAbsolutePath().normalize();
        if (root.startsWith(webRoot) || webRoot.startsWith(root)) {
            throw new IllegalStateException("AI staging storage must be outside the static web root");
        }
        this.tempDirectory = root.resolve("tmp").normalize();
        this.objectDirectory = root.resolve("objects").normalize();
        if (!tempDirectory.startsWith(root) || !objectDirectory.startsWith(root)) {
            throw new IllegalStateException("Invalid AI staging storage root");
        }
        try {
            Files.createDirectories(tempDirectory);
            Files.createDirectories(objectDirectory);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to initialize private AI staging storage");
        }
    }

    public PreparedFile prepare(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw bad("上传文件不能为空");
        }
        if (file.getSize() > properties.maxMaterialSizeBytes()) {
            throw new BusinessException(HttpStatus.PAYLOAD_TOO_LARGE, "文件超过预审材料大小上限");
        }
        String originalName = normalizeDisplayName(file.getOriginalFilename());
        String extension = extensionOf(originalName);
        String declaredType = normalizeContentType(file.getContentType());
        String expectedType = switch (extension) {
            case "pdf" -> "application/pdf";
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            default -> "";
        };
        if (!ALLOWED_TYPES.contains(declaredType) || !declaredType.equals(expectedType)) {
            throw bad("文件扩展名与声明类型不匹配");
        }

        String tempKey = opaqueKey("tmp");
        String objectKey = opaqueKey("obj") + "." + extension;
        Path tempPath = resolve(tempDirectory, tempKey);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] header = new byte[8];
            int headerLength = 0;
            long actualSize = 0;
            try (InputStream input = file.getInputStream();
                 OutputStream output = Files.newOutputStream(tempPath, StandardOpenOption.CREATE_NEW,
                         StandardOpenOption.WRITE)) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    actualSize += count;
                    if (actualSize > properties.maxMaterialSizeBytes()) {
                        throw new BusinessException(HttpStatus.PAYLOAD_TOO_LARGE, "文件超过预审材料大小上限");
                    }
                    digest.update(buffer, 0, count);
                    if (headerLength < header.length) {
                        int copy = Math.min(count, header.length - headerLength);
                        System.arraycopy(buffer, 0, header, headerLength, copy);
                        headerLength += copy;
                    }
                    output.write(buffer, 0, count);
                }
            }
            if (actualSize == 0 || !signatureMatches(declaredType, header, headerLength)) {
                deleteTempQuietly(tempKey);
                throw bad("文件内容与允许的 PDF/JPEG/PNG 类型不匹配");
            }
            return new PreparedFile(originalName, tempKey, objectKey, declaredType, extension, actualSize,
                    HexFormat.of().formatHex(digest.digest()));
        } catch (BusinessException exception) {
            deleteTempQuietly(tempKey);
            throw exception;
        } catch (IOException | NoSuchAlgorithmException exception) {
            deleteTempQuietly(tempKey);
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "预审材料暂存失败");
        }
    }

    public void promote(PreparedFile file) throws IOException {
        Path source = resolve(tempDirectory, file.tempObjectKey());
        Path target = resolve(objectDirectory, file.storedObjectKey());
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, target);
        }
    }

    public boolean objectExists(String key) {
        return Files.isRegularFile(resolve(objectDirectory, key));
    }

    public boolean tempExists(String key) {
        return Files.isRegularFile(resolve(tempDirectory, key));
    }

    public void deleteObject(String key) throws IOException {
        if (key != null) Files.deleteIfExists(resolve(objectDirectory, key));
    }

    public void deleteTemp(String key) throws IOException {
        if (key != null) Files.deleteIfExists(resolve(tempDirectory, key));
    }

    public void deleteTempQuietly(String key) {
        try {
            deleteTemp(key);
        } catch (IOException ignored) {
            // The periodic reconciler only removes server-generated keys from this private directory.
        }
    }

    public void deleteObjectQuietly(String key) {
        try {
            deleteObject(key);
        } catch (IOException ignored) {
            // Keep the metadata tombstone; the lifecycle reaper retries this generated object key.
        }
    }

    public void forEachTempKey(TempKeyConsumer consumer) throws IOException {
        try (DirectoryStream<Path> paths = Files.newDirectoryStream(tempDirectory)) {
            for (Path path : paths) {
                if (Files.isRegularFile(path) && path.getFileName().toString().startsWith("tmp_")) {
                    consumer.accept(path.getFileName().toString(), Files.getLastModifiedTime(path).toMillis());
                }
            }
        }
    }

    private Path resolve(Path directory, String key) {
        if (key == null || !key.matches("(?:tmp|obj)_[A-Za-z0-9_-]{24,64}(?:\\.[a-z]{1,5})?")) {
            throw new IllegalArgumentException("Invalid private object reference");
        }
        Path path = directory.resolve(key).normalize();
        if (!path.startsWith(directory)) throw new IllegalArgumentException("Invalid private object reference");
        return path;
    }

    String normalizeDisplayName(String input) {
        if (input == null) throw bad("文件名不能为空");
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFC);
        if (normalized.indexOf('/') >= 0 || normalized.indexOf('\\') >= 0
                || normalized.chars().anyMatch(character -> "<>:\"|?*".indexOf(character) >= 0)) {
            throw bad("文件名不能包含路径或保留字符");
        }
        for (int offset = 0; offset < normalized.length();) {
            int codePoint = normalized.codePointAt(offset);
            int type = Character.getType(codePoint);
            if (Character.isISOControl(codePoint) || type == Character.FORMAT
                    || type == Character.LINE_SEPARATOR || type == Character.PARAGRAPH_SEPARATOR
                    || type == Character.SURROGATE) {
                throw bad("文件名不能包含控制字符");
            }
            offset += Character.charCount(codePoint);
        }
        String value = normalized.strip();
        if (value.isBlank() || value.equals(".") || value.equals("..") || value.endsWith(".")) {
            throw bad("文件名不合法");
        }
        if (value.codePointCount(0, value.length()) > 200) throw bad("文件名过长");
        return value;
    }

    String normalizeContentType(String input) {
        return input == null ? "" : input.trim().toLowerCase(Locale.ROOT);
    }

    String extensionOf(String name) {
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
    }

    private String opaqueKey(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().replace("-", "");
    }

    private boolean signatureMatches(String contentType, byte[] header, int length) {
        if ("application/pdf".equals(contentType)) {
            return length >= 5 && header[0] == '%' && header[1] == 'P' && header[2] == 'D'
                    && header[3] == 'F' && header[4] == '-';
        }
        if ("image/jpeg".equals(contentType)) {
            return length >= 3 && (header[0] & 0xff) == 0xff && (header[1] & 0xff) == 0xd8
                    && (header[2] & 0xff) == 0xff;
        }
        if ("image/png".equals(contentType)) {
            byte[] signature = {(byte) 0x89, 'P', 'N', 'G', 0x0d, 0x0a, 0x1a, 0x0a};
            return length == signature.length && java.util.Arrays.equals(signature, header);
        }
        return false;
    }

    private BusinessException bad(String message) {
        return new BusinessException(HttpStatus.BAD_REQUEST, message);
    }

    public record PreparedFile(String originalFilename, String tempObjectKey, String storedObjectKey,
                               String contentType, String fileExtension, long fileSize, String sha256) {
    }

    @FunctionalInterface
    public interface TempKeyConsumer {
        void accept(String key, long modifiedAtMillis) throws IOException;
    }
}
