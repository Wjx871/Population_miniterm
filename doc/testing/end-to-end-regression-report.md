# End-to-end regression report

Environment: isolated MySQL Community Server 8.4.10 LTS on localhost, Java 17.0.10, backend port 18080. H2 tests remain the repeatable automated regression layer; MySQL checks validate production SQL and runtime compatibility.

| Chain | Preconditions and steps | Expected / actual result | Result | Defect |
|---|---|---|---|---|
| Authentication and RBAC | Login as viewer, population, household, approver and admin; call `/api/auth/me`, person search and a protected write | All five logins succeeded; unauthenticated request returned 401; viewer valid write returned 403 | Pass | - |
| Data scope and masking | Load four synthetic people in two scopes | viewer saw 0; population and approver saw 3 masked rows; household saw 3 full rows in region; admin saw all 4 full rows | Pass | - |
| Approval | Run phase-02 integration suite: create, submit, approve/reject and inspect trail | Generic and professional status synchronization and audit trail matched assertions | Pass | - |
| Migration out | Run phase-03 transaction suite including material, approval, execution and rollback | Current residence removed, archive produced, member state updated, rollback protected all writes | Pass | - |
| Migration in | Run phase-03 transaction suite | Current residence/member relation created only after approval and explicit execution | Pass | - |
| Person cancellation | Run phase-04 suite including death and normal cancellation | Residence archive, person/member/household linkage and logs matched assertions | Pass | - |
| Household cancellation | Run phase-04 suite | Archive created and household master retained as `CANCELLED` | Pass | - |
| Floating population / permit | Run phase-05 suite: register, issue, endorse, cancel, expire and rollback | Lifecycle state and logs matched assertions | Pass | - |
| Normal export | POST masked PERSON XLSX export against MySQL, then inspect phase-06 tests | MySQL request completed; whitelist, masking, formula protection, SHA-256 and download tests passed | Pass | - |
| Sensitive export | Run phase-06 approval, execute, secure download, counter and cleanup tests | No file before execution; authorization, expiry and cleanup matched assertions | Pass | - |
| Audit logs | Exercise login, person reads/writes and export | Authentication, business operation and export audit records remained queryable and sanitized | Pass | - |

## Defects

- `BUG-P07-001`: V4_005 had no database selection and used unconditional column/index additions. Root cause was that phase-five SQL had only been exercised through the H2-equivalent test schema. Fixed by adding `USE` and metadata-guarded stored procedures; first and second MySQL executions now pass and leave no procedures behind.

No failed verification is omitted. Browser-only visual walkthrough remains a manual acceptance item; API and production-build checks passed.

## Non-blocking technical debt

- Several endpoints still serialize Spring `PageImpl` directly. Replacing every public page contract with a project `PageResult` would require coordinated backend and frontend API migration; it was not mixed into this defect-focused phase. Existing response semantics and tests remain stable.
- Three integration suites still use deprecated `@MockBean`. The replacement affects test context wiring only and was deferred because transaction and MySQL correctness have higher priority.
- Vite route pages are already lazy-loaded, but global Element Plus registration leaves a 758.10 kB main chunk. A safe fix requires component-level imports and visual regression. A tested forced vendor group increased the largest chunk to 973.16 kB and was therefore rejected rather than committed.
