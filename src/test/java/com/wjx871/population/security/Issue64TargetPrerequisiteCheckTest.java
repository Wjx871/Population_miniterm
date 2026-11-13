package com.wjx871.population.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.util.Arrays;
import java.util.Locale;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Opt-in {@code TARGET_PREREQUISITE_CHECK} ONLY for #64 (NOT {@code TARGET_GREEN}).
 *
 * <p><strong>Explicit Security Warning (Blocker F):</strong>
 * Passing this prerequisite check ONLY confirms static/configuration preconditions
 * ({@code DURABLE_DB_REQUIRED} enum presence and isolated test DB coordinate format).
 * It NEVER proves that {@code DURABLE_DB_REQUIRED} is actually active at runtime,
 * NEVER proves that MySQL revocation commits/reads work, and NEVER replaces full
 * dynamic {@code TARGET_GREEN} verification via
 * {@code scripts/issue64/issue64_red_gate.py --stage target-green --allow-target-green}.
 *
 * <p>When {@code DURABLE_DB_REQUIRED} is absent or prerequisites are incomplete,
 * {@code TARGET_GREEN = NOT_RUN} and this check MUST fail cleanly.
 */
@EnabledIfSystemProperty(named = "issue64.target.prereq", matches = "true")
class Issue64TargetPrerequisiteCheckTest {

    private static final Pattern ISOLATED_TEST_DB_PATTERN =
            Pattern.compile("^issue64_test_[a-z0-9_]{4,40}$");

    @Test
    void targetPrerequisiteCheckOnly_doesNotProveTargetGreen() {
        boolean durableModePresent = Arrays.stream(RevocationProperties.Mode.values())
                .anyMatch(mode -> "DURABLE_DB_REQUIRED".equals(mode.name()));
        if (!durableModePresent) {
            fail("[TARGET_PREREQUISITE_CHECK] FAILED (TARGET_GREEN = NOT_RUN): "
                    + "RevocationProperties.Mode.DURABLE_DB_REQUIRED is not implemented yet. "
                    + "Refusing to treat SHARED_REDIS_REQUIRED or static placeholders as TARGET_GREEN.");
        }

        String sharedJdbcUrl = System.getProperty("issue64.shared.jdbc.url", "").trim();
        assertThat(sharedJdbcUrl)
                .as("[TARGET_PREREQUISITE_CHECK] Isolated shared JDBC URL (-Dissue64.shared.jdbc.url) required")
                .isNotBlank()
                .doesNotStartWithIgnoringCase("jdbc:h2:mem:");
        String lowerUrl = sharedJdbcUrl.toLowerCase(Locale.ROOT);
        assertThat(lowerUrl)
                .as("[TARGET_PREREQUISITE_CHECK] Production database population_miniterm is strictly forbidden")
                .doesNotContain("population_miniterm");

        String dbName = extractDatabaseName(sharedJdbcUrl);
        assertThat(dbName)
                .as("[TARGET_PREREQUISITE_CHECK] Target DB name must match isolated test schema pattern issue64_test_<id>")
                .matches(ISOLATED_TEST_DB_PATTERN);

        System.out.println("[TARGET_PREREQUISITE_CHECK] PASSED preconditions only. "
                + "NOTE: Passing this check does NOT constitute TARGET_GREEN dynamic security acceptance.");
    }

    private static String extractDatabaseName(String jdbcUrl) {
        String withoutQuery = jdbcUrl.split("\\?", 2)[0];
        int lastSlash = withoutQuery.lastIndexOf('/');
        if (lastSlash < 0 || lastSlash == withoutQuery.length() - 1) {
            return "";
        }
        return withoutQuery.substring(lastSlash + 1);
    }
}
