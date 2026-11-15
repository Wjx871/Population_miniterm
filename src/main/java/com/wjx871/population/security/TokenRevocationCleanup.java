package com.wjx871.population.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class TokenRevocationCleanup {
    private final JdbcTokenRevocationStore store;
    private final boolean enabled;
    public TokenRevocationCleanup(JdbcTokenRevocationStore store,
            @Value("${population.scheduling.enabled:true}") boolean enabled) {
        this.store = store;
        this.enabled = enabled;
    }
    @Scheduled(cron = "${security.jwt.revocation-cleanup-cron:0 0 3 * * *}")
    public void scheduled() { if (enabled) store.deleteExpired(); }
}
