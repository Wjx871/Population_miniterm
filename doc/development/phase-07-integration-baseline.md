# Phase 07 integration baseline

- Branch: `feat/project-integration`
- Starting commit: `e3b9572` (`merge: complete phase 06 export audit and permission controls`)
- `origin/develop` ancestor check: passed
- Initial worktree: clean
- Backend baseline: 144 tests passed, 0 failures, 0 errors, 0 skipped, 46.801 s
- Frontend baseline: Vite 8.1.4 production build passed; largest JavaScript chunk 758.10 kB
- Toolchain: Java 17.0.10, Node 22.18.0, npm 10.9.3, Maven Wrapper / Maven 3.9.16
- Migration list: V4_001 through V4_006 under `doc/database/migrations`
- Initial local database state: MySQL 9.4 service existed but was unsuitable and credentials were unavailable. An isolated MySQL 8.4.10 LTS instance was therefore used.
- Baseline warnings: Spring `PageImpl` serialization, deprecated `MockBean`, and Vite chunk size over 500 kB.
- Explicitly unimplemented: key-population workflow, release/restoration workflow, multi-level approval, physical card production, government-platform integration, message queues, microservices, cloud storage, SMS and email.

No real credentials, upload files, export files or local database files are part of the repository.
