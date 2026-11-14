package com.wjx871.population.security;

import java.time.Duration;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties("security.jwt.revocation")
public class RevocationProperties {
    public enum Mode { SINGLE_INSTANCE_LOCAL, SHARED_REDIS_REQUIRED, DURABLE_DB_REQUIRED }
    private Mode mode = Mode.SINGLE_INSTANCE_LOCAL;
    private boolean production;
    private boolean allowProductionSingleInstance;
    private String productionSingleInstanceApproval;
    private Duration cleanupInterval = Duration.ofSeconds(30);
    private int cleanupBatchSize = 256;
    private Duration cleanupGraceSkew = Duration.ofMillis(DurableRevocationStore.DEFAULT_CLEANUP_GRACE_SKEW_MS);
    private boolean allowZeroCutoffForTest = false;
}
