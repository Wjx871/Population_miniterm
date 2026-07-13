package com.wjx871.population.review;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class SecondaryBackendDataValidationTest {
    private static final Path DEMO = Path.of("doc/database/demo_data_household_migration.sql");
    private static final Pattern IDENTITY = Pattern.compile("(?<!\\d)(\\d{17}[0-9X])(?!\\d)");
    private static final int[] WEIGHTS = {7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2};
    private static final char[] CHECKS = {'1', '0', 'X', '9', '8', '7', '6', '5', '4', '3', '2'};

    @Test
    void convertedSqlUsesOnlySafeCurrentProjectConventions() throws Exception {
        String sql = Files.readString(DEMO, StandardCharsets.UTF_8);
        assertThat(sql).contains("USE population_miniterm", "DEMO-HH2-", "ON DUPLICATE KEY UPDATE");
        assertThat(sql.toUpperCase()).doesNotContain("FOREIGN_KEY_CHECKS", "DROP TABLE", "DROP DATABASE");
        assertThat(sql).doesNotContain("operator01", "approver01", "minio://", "admin_region",
                "residence_registration", "sys_approval_request", "sys_approval_log");
        assertThat(sql).doesNotContain("INSERT INTO business_application", "INSERT INTO migration_in",
                "INSERT INTO migration_out", "INSERT INTO residence_archive", "INSERT INTO application_material");
    }

    @Test
    void allConvertedIdentitiesAreValidUniqueAndDoNotConflictWithBaseDemo() throws Exception {
        String sql = Files.readString(DEMO, StandardCharsets.UTF_8);
        String baseDemo = Files.readString(Path.of("doc/database/demo_data.sql"), StandardCharsets.UTF_8);
        Matcher matcher = IDENTITY.matcher(sql);
        Set<String> identities = new HashSet<>();
        while (matcher.find()) {
            String identity = matcher.group(1);
            assertThat(validIdentity(identity)).as(identity).isTrue();
            identities.add(identity);
        }
        assertThat(identities).hasSize(9);
        assertThat(identities).allMatch(identity -> !baseDemo.contains(identity));
    }

    @Test
    void selfCheckAndArchitectureGuardrailsArePresent() throws Exception {
        String check = Files.readString(Path.of("doc/database/check_household_migration_demo.sql"), StandardCharsets.UTF_8);
        assertThat(check).contains("active_residence_per_person", "active_membership_per_person",
                "head_not_active_member", "completed_out_has_active_residence", "archive_orphan_person",
                "migration_in_duplicate_application", "demo_duplicate_identity", "demo_invalid_identity",
                "demo_member_orphan", "demo_residence_orphan", "required_demo_account_missing",
                "fake_minio_material");
        String pom = Files.readString(Path.of("pom.xml"), StandardCharsets.UTF_8).toLowerCase();
        assertThat(pom).doesNotContain("mybatis-plus");
        try (var paths = Files.walk(Path.of("src/main/java"))) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                String source = Files.readString(path, StandardCharsets.UTF_8);
                assertThat(source).as(path.toString()).doesNotContain("package com.example.population", "ApprovalGate");
            }
        }
    }

    private boolean validIdentity(String identity) {
        try {
            LocalDate.parse(identity.substring(6, 14), DateTimeFormatter.BASIC_ISO_DATE);
        } catch (DateTimeParseException exception) {
            return false;
        }
        int sum = 0;
        for (int index = 0; index < 17; index++) {
            sum += Character.digit(identity.charAt(index), 10) * WEIGHTS[index];
        }
        return identity.charAt(17) == CHECKS[sum % 11];
    }
}
