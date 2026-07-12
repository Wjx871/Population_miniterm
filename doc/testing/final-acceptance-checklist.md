# Final acceptance checklist

- [x] Complete SQL initialization passed on MySQL 8.4.10
- [x] V4_001-V4_006 sequential execution passed
- [x] All migrations passed a second execution
- [x] Historical person, household, membership, residence, certificate, migration and log rows retained
- [x] Backend started against MySQL 8 and all mappers initialized
- [x] Backend regression tests passed
- [x] Frontend production build passed
- [x] Five course roles logged in
- [x] Permission matrix and 401/403 checks passed
- [x] Data scope and masking checks passed
- [x] Migration-in and migration-out regression passed
- [x] Person and household cancellation regression passed
- [x] Floating registration and residence-permit regression passed
- [x] Approval workflow regression passed
- [x] Normal and sensitive export regression passed
- [x] Operation and export audit regression passed
- [x] Upload/download regression passed
- [x] Repeatable fictional demo data passed
- [x] Deployment and acceptance documentation completed
- [x] Sensitive credential and generated-file scan passed
- [x] `git diff --check` passed

Manual acceptance before a public demonstration: visually walk through every page in the target browser, confirm reverse-proxy configuration, replace all course passwords, and verify production backup/restore ownership.
