package com.wjx871.population.material;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wjx871.population.common.BusinessException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

class Issue3OwnedFileCompensationTest {
    @TempDir Path root;

    @Test
    void partialWriteFailureRemovesOnlyTheExclusivelyCreatedFile() throws Exception {
        Path unrelated = root.resolve("another-request.jpg");
        Files.writeString(unrelated, "SYNTHETIC_OTHER_REQUEST");
        LocalFileStorageService storage = new LocalFileStorageService(root.toString(), 1) {
            @Override protected void writeBytes(OutputStream output, byte[] bytes) throws IOException {
                output.write(bytes, 0, 2);
                throw new IOException("SYNTHETIC_DISK_FAILURE");
            }
        };
        assertThatThrownBy(() -> storage.store(file())).isInstanceOf(BusinessException.class).hasMessage("文件保存失败");
        try (var files = Files.list(root)) { assertThat(files.toList()).containsExactly(unrelated); }
        assertThat(Files.readString(unrelated)).isEqualTo("SYNTHETIC_OTHER_REQUEST");
    }

    @Test
    void inputCloseFailureCreatesNoFile() throws Exception {
        LocalFileStorageService storage = new LocalFileStorageService(root.toString(), 1);
        MockMultipartFile input = new MockMultipartFile("file", "synthetic.jpg", "image/jpeg", new byte[]{1,2,3}) {
            @Override public InputStream getInputStream() {
                return new ByteArrayInputStream(new byte[]{1,2,3}) {
                    @Override public void close() throws IOException { throw new IOException("SYNTHETIC_CLOSE_FAILURE"); }
                };
            }
        };
        assertThatThrownBy(() -> storage.store(input)).isInstanceOf(BusinessException.class).hasMessage("文件保存失败");
        try (var files = Files.list(root)) { assertThat(files.toList()).isEmpty(); }
    }

    @Test
    void sameShaAlwaysGetsDifferentOwnedPathsAndDeletingOnePreservesTheOther() throws Exception {
        LocalFileStorageService storage = new LocalFileStorageService(root.toString(), 1);
        var a = storage.store(file());
        var b = storage.store(file());
        assertThat(a.sha256()).isEqualTo(b.sha256());
        assertThat(a.stored()).isNotEqualTo(b.stored());
        storage.delete(a.stored());
        assertThat(Files.exists(Path.of(b.path()))).isTrue();
        assertThat(Files.readAllBytes(Path.of(b.path()))).containsExactly(1,2,3,4);
    }

    private MockMultipartFile file() { return new MockMultipartFile("file", "synthetic.jpg", "image/jpeg", new byte[]{1,2,3,4}); }
}
