package com.wjx871.population.release;

import static org.assertj.core.api.Assertions.assertThat;
import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class BackendV1ReleaseAuditTest {
    private static final Path ROOT = Path.of("").toAbsolutePath();

    @Test void productionHasNoLegacyResidentApiOrMapper() throws Exception {
        String production = text(ROOT.resolve("src/main"));
        assertThat(production).doesNotContain("/api/residents", "ResidentController", "ResidentMapper");
    }
    @Test void coreMasterDataHasNoPhysicalDeleteEndpoint() throws Exception {
        String production = text(ROOT.resolve("src/main"));
        assertThat(production).doesNotContain("DELETE FROM person", "DELETE FROM household");
        assertThat(Files.readString(ROOT.resolve("src/main/java/com/wjx871/population/person/PersonController.java")))
                .doesNotContain("@DeleteMapping");
    }
    @Test void requiredMigrationsArePresent() {
        for (int i = 1; i <= 10; i++) {
            String prefix = "V4_%03d_".formatted(i);
            assertThat(files(ROOT.resolve("doc/database/migrations")).stream()
                    .anyMatch(path -> path.getFileName().toString().startsWith(prefix))).isTrue();
        }
    }
    @Test void architectureRemainsFrozen() throws Exception {
        String pom = Files.readString(ROOT.resolve("pom.xml"));
        String production = text(ROOT.resolve("src/main/java"));
        assertThat(pom).contains("<version>1.0.0</version>", "<java.version>17</java.version>", "spring-boot-starter-security")
                .doesNotContain("mybatis-plus");
        assertThat(production).doesNotContain("ApprovalGate");
    }
    @Test void configurationUsesEnvironmentPlaceholders() throws Exception {
        String properties = Files.readString(ROOT.resolve("src/main/resources/application.properties"));
        assertThat(properties).contains("${DB_URL:", "${DB_USERNAME:", "${DB_PASSWORD:", "${JWT_SECRET:",
                "${REDIS_ENABLED:", "${APP_UPLOAD_DIR:", "${EXPORT_DIR:");
        assertThat(properties).doesNotContain("password=123456", "Bearer eyJ");
    }
    @Test void runtimeDirectoriesAreIgnored() throws Exception {
        String ignore = Files.readString(ROOT.resolve(".gitignore"));
        assertThat(ignore).contains("target/", "logs/", "data/uploads/", "data/exports/");
    }
    @Test void publicRoutesAreExplicitlyLimited() throws Exception {
        String config = Files.readString(ROOT.resolve("src/main/java/com/wjx871/population/security/SecurityConfig.java"));
        assertThat(config).contains("/api/auth/login", "/api/health", ".requestMatchers(\"/api/**\").authenticated()")
                .doesNotContain("/api/auth/logout\", \"/api/auth/me");
    }
    @Test void releaseDocumentationExists() {
        List<String> required = List.of("doc/api/backend-api-index.md", "doc/security/backend-permission-matrix.md",
                "doc/security/backend-sensitive-data-audit.md", "doc/database/check_backend_v1.sql",
                "doc/deployment/backend-v1-deployment.md", "doc/deployment/backend-v1-operations.md",
                "doc/database/backend-v1-database-guide.md", "doc/testing/backend-v1-final-test-report.md",
                "doc/development/backend-v1-completion-report.md", ".env.example");
        required.forEach(path -> assertThat(Files.exists(ROOT.resolve(path))).as(path).isTrue());
    }
    @Test void demoDataUsesCanonicalBusinessAndPermitStatuses() throws Exception {
        String demo = Files.readString(ROOT.resolve("doc/database/demo_data.sql"));
        assertThat(demo).contains("'RESIDENCE_PERMIT_FIRST_ISSUE'", "'ACTIVE',1")
                .doesNotContain("'RESIDENCE_PERMIT_APPLICATION'", "'VALID',1");
    }
    private static String text(Path root) throws IOException {
        StringBuilder result = new StringBuilder();
        for (Path path : files(root)) if (Files.isRegularFile(path)) result.append(Files.readString(path));
        return result.toString();
    }
    private static List<Path> files(Path root) {
        try (var stream = Files.walk(root)) { return stream.filter(Files::isRegularFile).toList(); }
        catch (IOException ex) { throw new IllegalStateException(ex); }
    }
}
