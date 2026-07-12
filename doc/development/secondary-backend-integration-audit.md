# Secondary backend integration audit

## Executive conclusion

The secondary backend cannot be merged directly. `develop` and `origin/household-migration` have no merge base, and the implementations use incompatible ORM, packages, security interception, response contracts, tables and approval semantics. No secondary Java source, dependency or schema definition is imported by this review.

The reusable part is limited to domain ideas, validation rules, test scenarios and consistency queries. All reusable data has been rewritten against the current schema as prerequisite data only; applications, materials, approvals, archives and completed migrations must still be created by the current APIs.

## Architecture comparison

| Comparison | Current project | Secondary backend | Compatible | Recommendation |
|---|---|---|---|---|
| Spring Boot | 3.5.3 | 3.2.5 | Partial | Keep current parent and dependency management |
| ORM | Plain MyBatis, mapper interfaces + XML | MyBatis-Plus 3.5.6 | No | Do not import base mappers/plugins |
| Package | `com.wjx871.population` | `com.example.population` | No | Never copy the secondary source tree |
| Bootstrap | `PopulationMinitermApplication` | `PopulationApplication` | No | Keep one current bootstrap class |
| Mapper scan | Spring/MyBatis mapper interfaces and XML | `@MapperScan` plus MyBatis-Plus | No | Keep current mapper registration |
| Authentication | Spring Security filter chain | MVC JWT interceptor | No | Keep Spring Security |
| JWT claims | Subject username; authorization reloaded through current user model | uid/uname/realName/permLevel/roleCode/dataScope/permCodes embedded | No | Do not accept secondary tokens |
| Permission annotations | `@PreAuthorize` | `@RequiresPermission` / `@RequiresLevel` AOP | No | Keep audited method security |
| Data scope | `CurrentUserContext` + `DataScopeCriteria` | Claim/AOP/service-specific scope | No | Reimplement any useful query using current scope criteria |
| Response | `ApiResponse<T>` | `Result<T>` | No | Do not add a second envelope |
| Pagination | Spring `Page` JSON contract | MyBatis-Plus page records | No | Adapt at API boundary only if later required |
| Global exception | Current `GlobalExceptionHandler` and security handlers | BizException hierarchy handler | Partial | Reuse error ideas, not classes |
| Redis | None | Optional permission cache | No | Do not introduce a runtime dependency |
| API documentation | Markdown API documentation | Knife4j/OpenAPI | Partial | Possible future documentation enhancement only |
| Person | `person`: `id_card`, `current_status_code`, no physical workflow deletion | Different names including `identity_no`, logical delete | No | Current table is authoritative |
| Household | `household_no`, `address`, `household_type`, version | `registered_address`, department-oriented fields | No | Map scenario values only |
| Household member | `relationship`, `status=ACTIVE/LEFT` | `relationship_code`, `member_status=CURRENT/LEFT` | No | Translate CURRENT to ACTIVE |
| Current residence | `residence`, unique current person | `residence_registration` | No | Never create the secondary table |
| Residence archive | `residence_archive`, generated during execute | Similar name but different columns/FKs | Partial | Let current execute create it |
| Migration in | Current professional record with `business_status` and version | Different code fields and completion columns | No | Create through current migration API |
| Migration out | Current professional record; archive created atomically on execute | Requires prebound archive id | No | Never pre-create archive links |
| Cancellation | `cancellation_record` and explicit execute | Separate completion endpoints/approval gate | No | Keep current workflow |
| Floating population | Dedicated application, current flag and lifecycle | Simpler direct record | No | Keep phase-five model |
| Residence permit | Dedicated permit and lifecycle logs | Different/general certificate assumptions | No | Keep phase-five model |
| Key population | Legacy table only; full workflow not implemented | Full controller/service workflow | Partial | Candidate for later rewrite, not this review |
| Generic certificate | Legacy `certificate`; residence permits excluded | Broader certificate model | Partial | Preserve legacy table; no redesign now |
| Application | `business_application`, current fields/status/version | Different columns and steps | No | Current application is authoritative |
| Material | Local controlled storage, hash and verification | MinIO URI examples and different fields | No | Never fabricate file rows |
| Approval | `approval_request` + `approval_log` | `sys_approval_request` + `sys_approval_log` | No | Keep current tables |
| Approval state machine | Submit, decide, then explicit professional execute | ApprovalGate can dispatch payload and land business immediately | No | ApprovalGate is prohibited |
| Operation log | Sanitized `operation_log` | Separate login/operation conventions | Partial | Reuse audit event ideas only |
| Export log | `data_export_log`, secure files/download/cleanup | No equivalent phase-six contract | No | Current implementation already exceeds reference |
| Tests | H2 integration/transaction tests plus MySQL verification | Mockito/reflection unit tests; Redis test may skip | Partial | Scenario ideas may become current integration tests |
| MySQL initialization | One final script verified on MySQL 8.4 | Multiple independent schema/data scripts | No | Keep current initialization source |
| Incremental migration | V4_001-V4_006, repeat verified | No compatible ordered migration chain | No | Do not apply secondary DDL |

## Business semantic conflicts

| Topic | Current rule | Secondary rule | Conflict / portability | Recommendation |
|---|---|---|---|---|
| Approval execution | Approval only reaches APPROVED; authorized endpoint explicitly executes | ApprovalGate may deserialize and land changes during approval | Critical; not portable | Keep explicit execute |
| Migration states | Generic and professional status synchronized, optimistic version | Separate application/current-step/completed-at model | Yes | Use current enums only |
| Cancellation states | Approved then explicit execute | L3 completion/approval linkage | Yes | Do not import state machine |
| Permit states | Dedicated application, permit and lifecycle log | Different simplified model | Yes | Keep phase-five implementation |
| Current/history | `residence` is current; archive only on transaction | `residence_registration` plus manually seeded archives | Structural | Translate only prerequisites |
| Physical deletion | Person/household master retained | Logical-delete conventions and direct registration deletion | Partial | Preserve current master records |
| One active residence | Unique person/current residence semantics | Generated/dedup registration model | Same intent, different mechanism | Reuse invariant in self-check |
| One active membership | ACTIVE/LEFT and unique pair | CURRENT/LEFT with virtual dedup | Same intent, different values | Translate CURRENT to ACTIVE |
| Head consistency | `head_person_id` must match ACTIVE HEAD member | Automatic HEAD insertion | Same intent | Seed both explicitly and check |
| Migration-out result | Execute archives and removes current residence; membership becomes LEFT | Similar transaction idea | Rule reusable, code not | Retain as API/test expectation |
| Cancellation result | Archive/status updates, master retained | Completion implementation differs | Partial | Reuse test scenario only |
| Material verification | Required material is checked before submit/execute according to current services | Central assert gate, MinIO examples | Partial | Reuse “required verified” rule, not storage rows |
| Scope/masking | Central data scope and masking service | JWT/AOP scope; no equivalent phase-six masking contract | No | Current security remains mandatory |
| Legacy certificate permit | Residence permits deliberately separated from certificate | Generic certificate includes broad documents | Conflict | Do not copy permit rows into certificate |

## Reference SQL compatibility

The supplied SQL performs inserts into 15 tables and begins with `SET FOREIGN_KEY_CHECKS=0`; it must not be executed.

| Original area | Original table/fields | Current mapping | Risk | Convertible | Safe conversion |
|---|---|---|---|---|---|
| B | `admin_region` | No current table | Missing table | No | Keep region codes as scalar household/residence values |
| C | `sys_department.department_type_code` | `sys_department` has different fields and seeded codes | Missing column/codes | No | Use existing department/account subqueries only |
| D | `person.gender_code/identity_no/contact_address/record_status_code` | `gender/id_card/current_address/status/current_status_code` | Column/status mismatch; invalid IDs | Yes | Use fictional validated identities and current values |
| D | fixed natural identities | Existing demo identities differ | Collision/privacy risk | Yes | New `DEMO-HH2` identities only |
| E | `household.registered_address/department_id` | `address`; no equivalent required department field | Column mismatch | Yes | Omit unsupported field |
| F | `relationship_code/member_status=CURRENT` | `relationship/status=ACTIVE` | Enum mismatch | Yes | Translate values |
| G | `residence_registration` | `residence` | Missing table and different columns | Yes | Insert only current prerequisite residence |
| H | `business_application.*_code/current_step/submit_user_id` | Different current application contract | Widespread column/state mismatch | No | Create through professional API |
| I | `application_material.storage_uri` | `storage_path` with real local file contract | MinIO/fake file/hash | No | Do not insert material rows |
| J | `sys_approval_request` | `approval_request` | Missing table/fields | No | Submit and decide through API |
| J | `sys_approval_log` | `approval_log` | Missing table/step model | No | Let approval service write logs |
| K | pre-created `residence_archive` | Current archive created by execute | Violates transaction and pending case | No | Do not seed completed history |
| L | `migration_in.in_type_code/.../completed_at` | Different fields/status/version | Column/state mismatch | No | Create via `/api/migrations/in` |
| M | `migration_out.out_type_code/archive_id/completed_at` | Different current record | Prebinding archive violates execute | No | Create via `/api/migrations/out` |
| N | broad `certificate` fields | Legacy certificate has different fields | Column mismatch | Limited | Not needed for migration demo |
| O | floating `register_date/handling_department_id` | Dedicated phase-five columns | Column/lifecycle mismatch | No | Use existing phase-five demo data |

Additional risks: fixed primary keys 90200/95001 etc.; non-existent `operator01` and `approver01`; status values `CURRENT`, `SUBMITTED` and `UNDER_REVIEW` used in incompatible locations; pending migration 90209 already has an archive; completed out records coexist with seeded current residences/members; repeated material rows share names/URIs; and the script is not safely repeatable without suppressing foreign keys.

## Identity validation

Twelve unique 18-character identities were found. Dates are syntactically valid, but only 3 have a correct GB 11643 checksum. None conflicts with current `demo_data.sql`.

| Original | Result | Suggested fictional replacement |
|---|---|---|
| 110105195812121005 | Invalid checksum | 110105195812121007 |
| 110105196507177777 | Invalid checksum | 110105196507177770 |
| 110105198501151001 | Invalid checksum | 11010519850115100X |
| 110105198703201002 | Invalid checksum | 110105198703201001 |
| 110105199511015001 | Invalid checksum | 110105199511015006 |
| 110105201506301003 | Invalid checksum | 110105201506301008 |
| 110105201809051004 | Invalid checksum | 11010520180905100X |
| 110106198202022001 | Valid | Not reused |
| 310101195510103001 | Invalid checksum | 310101195510103008 |
| 310101195611113002 | Valid | Not reused |
| 310104199002024001 | Valid | Not reused |
| 310104199203054002 | Invalid checksum | 310104199203054004 |

The converted file uses nine newly generated fictional identities rather than silently correcting and reusing the reference people.

## Reuse classification

- A — directly reference rules: one current residence, one current membership, head/HEAD consistency, migration-out archive/member invariants, checksum and phone validation, consistency-query patterns.
- B — rewrite before reuse: household/collective-household scenarios, region tree idea, generic certificate ideas, data dictionary additions, transaction test cases and API documentation concepts.
- C — already implemented: migration-managed household/member consistency, migration in/out, archive, material verification, approval, RBAC/data scope, masking, operation/export audit and permit lifecycle.
- D — prohibited: unrelated-history merge, MyBatis-Plus, Redis permission cache, secondary JWT/interceptor, `Result<T>`, custom permission AOP, ApprovalGate, secondary DDL and MinIO fake material data.
- E — possible later work: key-population workflow, administrative-region query tree, stronger identity validator and structured OpenAPI documentation.

| Future item | Current gap | Reusable idea | Must not copy | Current-architecture approach | Suggested branch | Priority |
|---|---|---|---|---|---|---|
| Household query/maintenance API | Frontend calls `/api/households`, but current backend has no household CRUD controller | Household/member endpoint scenarios | Secondary entities, MyBatis-Plus and automatic approval landing | Add current-schema mapper XML/service/controller with `@PreAuthorize`, data scope and masking | `feat/household-query-management` | High |
| Identity validator | Request regex checks shape, not checksum | Checksum/date algorithm | Hutool/entity coupling | Jakarta validator shared by DTOs and demo tests | `feat/identity-validation` | High |
| Key population | Legacy table without complete workflow | Domain categories and release cases | ApprovalGate/MyBatis-Plus service | Specialized application + status listener + explicit execute | `feat/key-population-management` | Medium |
| Region tree | Scalar region codes only | Parent/child query semantics | Replacing household schema | Add read-only region reference table/API with `@PreAuthorize` | `feat/administrative-region-reference` | Medium |
| OpenAPI | Markdown only | Endpoint discoverability | Knife4j security replacement | Add documentation dependency/config without changing controllers/security | `chore/openapi-documentation` | Low |

## Architectural impact

None. The review adds documentation, prerequisite demo SQL, a read-only consistency script and static tests. It does not change production Java, pom.xml, frontend, schema initialization, migrations, API paths, approval behavior or security behavior.
