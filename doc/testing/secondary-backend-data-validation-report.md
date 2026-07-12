# Secondary backend data validation report

## Inputs and baseline

- Reference report: `D:/qq/项目总结报告.md` (read-only)
- Reference SQL: `D:/qq/test_data_households_migrations.sql` (never executed)
- Current baseline: `develop@c55ef9c`
- Baseline regression: 144 tests passed, 0 failed/error/skipped
- Reference branch history: `origin/household-migration` has no merge base with develop

## Static findings

- Original inserted tables: 15
- Unique identity numbers: 12
- Valid identity numbers: 3
- Invalid identity numbers: 9
- Base-demo identity collisions: 0
- Forbidden constructs: foreign-key checks disabled, MinIO fake paths, secondary accounts, secondary approval tables and current-residence table
- Business inconsistency: pending/under-review records include pre-created archives; completed/pending rows are manually composed outside the current explicit-execute transaction

## Converted data

`demo_data_household_migration.sql` contains nine fictional people and four households:

- family household: head, spouse and child with active memberships/residences;
- collective household: one head and two members;
- migration-out candidate: ACTIVE residence and membership in `DEMO-HH2-SOURCE`;
- migration-in candidate: `PENDING` person with no residence;
- cross-district target: active target household in another region.

No application, material, approval, archive or migration record is forged. The current professional APIs remain responsible for all workflow state.

## Automated validation

- `SecondaryBackendDataValidationTest`: 3 tests passed.
- Validates all converted identities, uniqueness and non-conflict with base demo.
- Rejects `FOREIGN_KEY_CHECKS`, DROP, MinIO, secondary accounts/tables and direct completed-business inserts.
- Guards against MyBatis-Plus, Redis, `com.example.population` and ApprovalGate entering the project.

Final Maven regression: 147 tests passed, 0 failures, 0 errors and 0 skipped in 45.935 seconds.

## MySQL verification

- Server: isolated MySQL Community Server 8.4.10 LTS on `127.0.0.1:3409`.
- Database: `population_miniterm_secondary_review` only.
- Current full initialization: passed; 30 tables.
- Existing `demo_data.sql`: passed.
- Converted import, first run: passed.
- Converted import, second run: passed.
- Converted rows: 9 people, 4 households, 8 active memberships and 8 current residences. The inbound candidate has no residence.
- Self-check: all 18 reported `abnormal_count` values were zero.
- Backend startup against the database: passed; all mappers initialized.
- API smoke: admin BCrypt login and lookup of the migration-in candidate passed.
- Existing gap observed: `/api/households` is referenced by the frontend but no current backend controller maps it; this is recorded as future work and is unrelated to the converted SQL.
- Foreign-key, unique-key and CHECK failures: none.
- Cleanup: performed after verification; no database credential was written to the repository.
