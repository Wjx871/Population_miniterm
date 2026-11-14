package com.wjx871.population.security;

import static org.assertj.core.api.Assertions.fail;

import java.net.URI;
import java.util.Arrays;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Opt-in TARGET_GREEN acceptance gate entry point for #64 ({@code DURABLE_DB_REQUIRED}).
 *
 * <p>Run explicitly only once production {@code DURABLE_DB_REQUIRED} persistence is implemented:
 * {@code .\mvnw.cmd test "-Dissue64.target.green=true" "-Dissue64.target.jdbcUrl=jdbc:mysql://127.0.0.1:3306/population_issue64_test" "-Dtest=Issue64DurableDbTargetGateTest"}
 *
 * <p>Strict target-mode invariants:
 * <ol>
 *   <li>Must NEVER fall back to {@code SHARED_REDIS_REQUIRED} or claim GREEN on legacy mode.</li>
 *   <li>Must NEVER accept per-JVM isolated {@code jdbc:h2:mem:} URLs as a shared durable store.</li>
 *   <li>Must NEVER target a production database name or non-loopback host.</li>
 *   <li>Must verify: (a) missing/UNINITIALIZED/INVALID checkpoint fails closed with HTTP 503 and
 *       blocks startup; (b) Redis outage still authenticates safely via MySQL (revoked old JWT =
 *       401, fresh JWT = 200, /api/health = 200); (c) empty Redis recovery keeps revoked JWT at
 *       401 across two JVMs sharing one authoritative database; (d) MySQL outage fails closed
 *       with HTTP 503.</li>
 * </ol>
 */
@EnabledIfSystemProperty(named = "issue64.target.green", matches = "true")
class Issue64DurableDbTargetGateTest {

    @Test
    void durableDbModeMustRejectRevokedJwtAcrossOutageAndEmptyRedisRecovery() {
        requireDurableDbModeImplemented();
        String sharedTestJdbcUrl = requireSafeSharedTestJdbcUrl(resolveTargetJdbcUrl());

        // Once DURABLE_DB_REQUIRED is implemented on src/main, this gate must boot two JVMs
        // wired to `sharedTestJdbcUrl` and one isolated Redis instance and assert:
        //   1. UNINITIALIZED / missing / INVALID checkpoint -> startup refusal and HTTP 503
        //   2. Ready checkpoint -> logout commits to MySQL before 200; both JVMs reject old JWT (401)
        //   3. Redis outage -> old JWT returns 401 and fresh JWT returns 200 via MySQL (not 503)
        //   4. Empty Redis recovery -> old JWT remains 401 on both JVMs, fresh JWT returns 200
        //   5. Cutoff advance on MySQL -> immediately rejects pre-cutoff tokens across both JVMs (0ms cache lag)
        //   6. MySQL primary outage -> fail-closed HTTP 503 while /api/health remains HTTP 200
        fail("TARGET_GREEN_CONTRACT_UNEXECUTED: wire dual-JVM runner against "
                + sanitizeJdbcUrl(sharedTestJdbcUrl) + " after implementing DURABLE_DB_REQUIRED");
    }

    static void requireDurableDbModeImplemented() {
        boolean implemented = Arrays.stream(RevocationProperties.Mode.values())
                .anyMatch(mode -> "DURABLE_DB_REQUIRED".equals(mode.name()));
        if (!implemented) {
            fail("TARGET_GREEN_NOT_IMPLEMENTED: RevocationProperties.Mode.DURABLE_DB_REQUIRED does not exist yet "
                    + "(TARGET_GREEN=NOT_RUN; refusing to fall back to SHARED_REDIS_REQUIRED)");
        }
    }

    static String requireSafeSharedTestJdbcUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            fail("TARGET_GREEN_CONFIG_ERROR: missing -Dissue64.target.jdbcUrl or ISSUE64_TARGET_DB_URL");
        }
        String url = rawUrl.trim();
        String lower = url.toLowerCase(Locale.ROOT);
        if (lower.startsWith("jdbc:h2:mem:")) {
            fail("TARGET_GREEN_CONFIG_ERROR: per-JVM isolated jdbc:h2:mem: is forbidden for TARGET_GREEN");
        }
        if (lower.startsWith("jdbc:mysql://")) {
            URI uri = URI.create(url.substring("jdbc:".length()));
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            String path = uri.getPath() == null ? "" : uri.getPath().replaceFirst("^/", "").toLowerCase(Locale.ROOT);
            if (!"127.0.0.1".equals(host) && !"localhost".equals(host)) {
                fail("TARGET_GREEN_CONFIG_ERROR: non-local MySQL host rejected: " + sanitizeJdbcUrl(url));
            }
            if (path.isEmpty() || (!path.contains("test") && !path.contains("issue64"))) {
                fail("TARGET_GREEN_CONFIG_ERROR: non-test MySQL database rejected: " + sanitizeJdbcUrl(url));
            }
            return url;
        }
        if (lower.startsWith("jdbc:h2:tcp://") || lower.startsWith("jdbc:h2:file:")) {
            if (!lower.contains("test") && !lower.contains("issue64")) {
                fail("TARGET_GREEN_CONFIG_ERROR: non-test H2 shared path rejected: " + sanitizeJdbcUrl(url));
            }
            return url;
        }
        fail("TARGET_GREEN_CONFIG_ERROR: unsupported shared test JDBC URL: " + sanitizeJdbcUrl(url));
        return url;
    }

    private static String resolveTargetJdbcUrl() {
        String prop = System.getProperty("issue64.target.jdbcUrl");
        if (prop != null && !prop.isBlank()) {
            return prop;
        }
        return System.getenv("ISSUE64_TARGET_DB_URL");
    }

    private static String sanitizeJdbcUrl(String url) {
        int queryIdx = url.indexOf('?');
        String base = queryIdx >= 0 ? url.substring(0, queryIdx) : url;
        int semiIdx = base.indexOf(';');
        return semiIdx >= 0 ? base.substring(0, semiIdx) : base;
    }
}
