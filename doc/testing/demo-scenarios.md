# Demonstration scenarios

All local course accounts use initial password `123456`; change it after initialization. Run `doc/database/demo_data.sql` only in a disposable demonstration database.

| Role | Entry | Steps | Expected result | Data IDs |
|---|---|---|---|---|
| viewer | Dashboard / population | Login, open population and statistics | Read-only menu; department-scoped masked data; writes return 403 | 700001-700004 |
| population | Floating population / residence permits | Inspect registration, create an application and submit it | Region-scoped records and masked identity/phone; permit becomes executable only after approval | `DEMO-FLOAT-001`, `DEMO-PERMIT-001` |
| household | Migration / cancellation | Use migration-out person, migration-in person and pending household | Approved requests can be explicitly executed; archives and operation logs appear | `DEMO-HH-OUT`, `DEMO-HH-IN`, `DEMO-HH-CANCEL` |
| approver | Approval center | Open submitted professional requests and approve/reject | Approval trail records actor, decision and time; no professional execution occurs automatically | application numbers created during demo |
| admin | Users / logs / exports | Inspect permissions, normal export, sensitive export approval execution and download | All-scope full-data view; export log and download count update; storage path is never returned | person IDs 700001-700004 |

Use fictional data only. Do not upload identity documents or other real personal material during demonstrations.
