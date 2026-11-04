-- V4_017: indexes for bounded S4-A token-session retention cleanup.
-- Apply after V4_016. V4_016 remains unchanged for databases where it may already be installed.
USE population_miniterm;

ALTER TABLE ai_resolution_session
    ADD KEY idx_ai_resolution_cleanup_expiry (status, expires_at, resolution_id);

ALTER TABLE ai_resolution_session
    ADD KEY idx_ai_resolution_cleanup_revoked (status, updated_at, resolution_id);
