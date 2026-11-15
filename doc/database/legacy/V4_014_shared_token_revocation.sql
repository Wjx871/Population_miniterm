-- Incremental migration; apply to the primary before starting this application version.
-- No raw JWT or user details are stored. Replication distributes the schema to a replica.
CREATE TABLE IF NOT EXISTS security_token_revocation (
    token_hash CHAR(64) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (token_hash),
    KEY idx_token_revocation_expires_at (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
