package com.wjx871.population.material;

import com.wjx871.population.common.BusinessException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** Resolves only database-authorized stage object keys; it is available when AI endpoints are disabled. */
@Service
public class BoundStageMaterialStorage {
    private static final Pattern STAGE_ID = Pattern.compile("stg_[A-Fa-f0-9]{32}");
    private static final Pattern OBJECT_KEY = Pattern.compile("obj_[A-Za-z0-9_-]{24,64}\\.[a-z]{1,5}");
    private static final long DEFAULT_MAX_BYTES = 10L * 1024L * 1024L;

    private final Path objectRoot;
    private final String maxSizeMb;

    public BoundStageMaterialStorage(@Value("${population.ai.precheck.storage-dir:./data/private-ai-stage}") String dir,
                                     @Value("${population.ai.precheck.max-material-size-mb:10}") String maxSizeMb) {
        this.objectRoot = Path.of(dir).toAbsolutePath().normalize().resolve("objects").normalize();
        this.maxSizeMb = maxSizeMb;
    }

    public boolean verify(String stageMaterialId, String objectKey, long expectedSize, String expectedSha256) {
        Path path = resolve(stageMaterialId, objectKey);
        if (expectedSize <= 0 || expectedSize > maxBytes() || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(path)) return false;
        try {
            if (Files.size(path) != expectedSize) return false;
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long read = 0;
            byte[] buffer = new byte[8192];
            try (InputStream input = Files.newInputStream(path)) {
                int count;
                while ((count = input.read(buffer)) != -1) {
                    read += count;
                    if (read > expectedSize || read > maxBytes()) return false;
                    digest.update(buffer, 0, count);
                }
            }
            return read == expectedSize && HexFormat.of().formatHex(digest.digest()).equalsIgnoreCase(expectedSha256);
        } catch (IOException | NoSuchAlgorithmException exception) {
            return false;
        }
    }

    public Resource load(String stageMaterialId, String objectKey, long expectedSize, String expectedSha256) {
        if (!verify(stageMaterialId, objectKey, expectedSize, expectedSha256)) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "材料文件不存在或不可用");
        }
        try {
            return new UrlResource(resolve(stageMaterialId, objectKey).toUri());
        } catch (Exception exception) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "材料文件不存在或不可用");
        }
    }

    public void delete(String stageMaterialId, String objectKey) throws IOException {
        Files.deleteIfExists(resolve(stageMaterialId, objectKey));
    }

    private Path resolve(String stageMaterialId, String objectKey) {
        if (stageMaterialId == null || !STAGE_ID.matcher(stageMaterialId).matches()
                || objectKey == null || !OBJECT_KEY.matcher(objectKey).matches()) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "材料文件不存在或不可用");
        }
        Path path = objectRoot.resolve(objectKey).normalize();
        if (!path.startsWith(objectRoot)) throw new BusinessException(HttpStatus.NOT_FOUND, "材料文件不存在或不可用");
        return path;
    }

    private long maxBytes() {
        try {
            long configured = Long.parseLong(maxSizeMb);
            if (configured > 0) return Math.multiplyExact(configured, 1024L * 1024L);
        } catch (RuntimeException ignored) {
            // AI-disabled application startup must not depend on AI-only settings being valid.
        }
        return DEFAULT_MAX_BYTES;
    }
}
