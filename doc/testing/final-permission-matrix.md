# Final permission matrix

All controller methods are protected by `@PreAuthorize`; `/api/auth/login` is the only anonymous business endpoint. The phase-six reflection test covers all 81 controller methods and remains part of the 144-test regression suite.

| Role | Module | Query | Create | Edit | Approve | Execute | Export | Full sensitive data |
|---|---|---:|---:|---:|---:|---:|---:|---:|
| viewer | Population / household / statistics | Yes, department scope | No | No | No | No | Normal masked | No |
| population | Population / floating / permit | Yes, region scope | Yes | Yes | No | Floating registration | Normal + sensitive apply | No |
| household | Household / migration / cancellation | Yes, region scope | Yes | Yes | No | Yes | Normal + sensitive execute/download | Yes |
| approver | Applications / approvals | Yes, region scope | No | No | Yes | No | Logs/download as granted | No |
| admin | All modules | Yes, all scope | Yes | Yes | Yes | Yes | Normal and sensitive | Yes |

System user, role, department and permission administration requires the corresponding `system:*` permissions; only the administrator role receives management permissions. Downloads require material-view or export-download permission in addition to record-level authorization.
