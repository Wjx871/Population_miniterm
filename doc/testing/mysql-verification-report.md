# MySQL 8 verification report

## Environment

- Server: MySQL Community Server 8.4.10 LTS, isolated local instance bound to `127.0.0.1:3408`
- Verification databases: `population_miniterm_fresh`, `population_miniterm_upgrade`, `population_miniterm_repeat`
- Client character set: `utf8mb4`; server collation: `utf8mb4_0900_ai_ci`
- Credentials were supplied only to the process environment and were not written to source control.

## Complete initialization

- Start: 2026-07-11 20:12:31 +08:00
- End: 2026-07-11 20:12:33 +08:00
- Result: success
- Tables: 30
- `information_schema.statistics` entries: 148
- Table constraints: 119
- Seed users / roles / permissions: 5 / 5 / 55
- Exceptions: none

The application subsequently started against this database. MyBatis XML files, all mapper beans, scheduled jobs, JSON serialization, pagination, authentication and XLSX export initialized successfully. All five BCrypt demonstration accounts logged in with `123456`.

## Historical upgrade and idempotency

The phase-01 tagged `population_miniterm.sql` was extracted byte-for-byte and loaded into the upgrade database. Synthetic legacy rows were added for user-linked person, household, household member, current residence, migration-out, certificate and operation log records.

| Migration | First run | Second/third verification | Fix |
|---|---|---|---|
| V4_001 | Pass | Pass | None |
| V4_002 | Pass | Pass | None |
| V4_003 | Pass | Pass | None |
| V4_004 | Pass | Pass | None |
| V4_005 | Initially failed; pass after fix | Pass | Added database selection and metadata-guarded column/rename/index operations |
| V4_006 | Pass | Pass | Added explicit database selection |

Before and after repeat migration, the permission/grant/person/household/migration/log counts remained `55|159|1|1|1|1`. Legacy IDs remained 9001, migration status was backfilled to `DRAFT`, and no `phase%` stored procedure remained. No business table was dropped and no legacy row was silently cancelled, completed or deleted.

## Repeatable automation

`scripts/verify-mysql.ps1` passed both final-schema initialization and two-pass upgrade verification. It accepts only the three documented test database names, requires an explicit reset switch before dropping one of them, reads connection details from environment variables, stops on the first MySQL error, and removes temporary rewritten SQL files.
