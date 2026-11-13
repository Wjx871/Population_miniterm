package com.wjx871.population.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Separate opt-in TARGET_GREEN prerequisite & acceptance entry for #64
 * ({@code DURABLE_DB_REQUIRED} mode).
 *
 * <p>Deliberately separated from {@link Issue64DurabilityRedGateTest} (which characterizes
 * {@code BASELINE_RED} under {@code SHARED_REDIS_REQUIRED} only):
 * <ul>
 *   <li>Requires {@code DURABLE_DB_REQUIRED} to exist in {@link RevocationProperties.Mode}.</li>
 *   <li>Requires an explicit shared persistent JDBC URL via {@code -Dissue64.shared.jdbc.url}
 *       (rejects isolated per-JVM {@code jdbc:h2:mem:} stand-ins).</li>
 *   <li>In the current design/RED round, {@code TARGET_GREEN = NOT_RUN}; invoking this gate
 *       before target implementation MUST fail cleanly rather than faking a pass.</li>
 * </ul>
 *
 * <p>Full dual-JVM target acceptance (including Redis outage survival via MySQL and empty Redis
 * recovery) is orchestrated via:
 * {@code python scripts/issue64/issue64_red_gate.py --stage target-green --shared-jdbc-url ...}
 */
@EnabledIfSystemProperty(named = "issue64.target.green", matches = "true")
class Issue64TargetGreenGateTest {

    @Test
    void durableDbRequiredPreconditionsMustBeSatisfiedBeforeTargetAcceptance() {
        boolean durableModePresent = Arrays.stream(RevocationProperties.Mode.values())
                .anyMatch(mode -> "DURABLE_DB_REQUIRED".equals(mode.name()));
        if (!durableModePresent) {
            fail("TARGET_GREEN_NOT_READY (NOT_RUN): RevocationProperties.Mode.DURABLE_DB_REQUIRED "
                    + "is not implemented yet; refusing to fake target-green acceptance under SHARED_REDIS_REQUIRED.");
        }

        String sharedJdbcUrl = System.getProperty("issue64.shared.jdbc.url", "").trim();
        assertThat(sharedJdbcUrl)
                .as("TARGET_GREEN requires a shared authoritative persistent DB URL (-Dissue64.shared.jdbc.url)")
                .isNotBlank()
                .doesNotStartWithIgnoringCase("jdbc:h2:mem:");
    }
}
