# #68 / #71 独立复核结论登记（2026-11-11）

结论由用户提供：ChatGPT 已独立复核，#68 和 #71 均为 **REVIEW_PASSED / ALREADY_INTEGRATED**。本轮仅登记结论，两项账本状态由 `CODE_CANDIDATE` 变为 `FIXED_VERIFIED`；不代表生产部署或生产规模性能已验证。

## 基线与版本核验

- 个人 integration 基线：`445f0f19d564e8911fad3083040c80fb70cd2dd8`；原实现提交：`c3fb7eb1a70e793a85b2cab07764195a99167a81`。`git merge-base --is-ancestor` 返回成功。
- 下列文件在原实现提交和基线的 Git blob 完全一致：

| 文件 | Blob SHA |
|---|---|
| `src/main/resources/mapper/Phase11QueryMapper.xml` | `b8815280863764584570f4c703508622ad68c1a0` |
| `src/main/resources/mapper/ComprehensiveQueryMapper.xml` | `cdb47eab170142b5895cc59bc242f3cc12650d59` |
| `src/main/java/com/wjx871/population/query/Phase11QueryService.java` | `8f73726a9eef896b52da223495955cd2801ecd7b` |
| `src/test/java/com/wjx871/population/query/Issue6871ComprehensiveQueryIntegrationTest.java` | `48800872e12095b177d7c132ffc2814100ea6e70` |
| `src/test/java/com/wjx871/population/query/Issue6871MySqlIT.java` | `7a986da76f8620559a445dca6bd08d1f89196778` |

## 缺陷、修复和边界

**#68 HIGH**：旧 Phase11 综合查询没有投影流动登记、居住证和迁移字段。现有实现关联 `ACTIVE` 且 `current_flag=1` 的当前流动登记和居住证，最近迁移只取 `COMPLETED`，同日 IN/OUT 确定取 OUT；服务端对居住证号脱敏，身份证、手机号和地址沿用既有脱敏路径。

**#71 MEDIUM**：旧 Phase11 REGION 仅按户籍过滤，遗漏当前流动人口；旧兼容查询还允许任意状态户籍或历史业务申请兜底。现在两套查询的 REGION 授权依据统一为授权区划集合内的 ACTIVE 户籍，或 ACTIVE 且 `current_flag=1` 的流动登记。INACTIVE 户籍、CLOSED 或 `current_flag=NULL` 流动登记、无关历史业务申请、仅有当前居住证或过去迁移，均不单独授予 REGION 权限。空 REGION 集合返回 403；SELF、DEPARTMENT、ALL 保持既有语义。

覆盖接口为 `GET /api/query/persons`、`GET /api/queries/persons` 和 `GET /api/queries/persons/{personId}`。授权确定用户可以看到哪些人员；`regionCode`、`residenceStatus` 等业务参数在授权基础上继续筛选，不能代替授权。历史回归覆盖列表、详情、分页与 count，一人多条迁移历史不应造成列表重复或计数膨胀。详情和过滤规则见 [原施工报告](issue68-71-comprehensive-query-scope.md)。

## 历史动态验证

以下均为历史证据，本轮没有运行 Maven、H2 或 MySQL，也未连接或部署生产数据库：

- 旧版真实 HTTP/JWT/H2：5 项中 3 项 RED，见 [RED 摘录](evidence/issue68-71-red.txt) 和 [完整日志](evidence/issue68-71-red-full.txt)。
- 修复后 H2：6 项通过，见 [H2 GREEN](evidence/issue68-71-h2-green.txt)。
- 修复后 MySQL 8.4.8、REPEATABLE-READ：7 项通过、零失败/错误/跳过；`ONLY_FULL_GROUP_BY` 保持启用。见 [MySQL 摘录](evidence/issue68-71-mysql.txt)、[完整日志](evidence/issue68-71-mysql-full.txt)、[EXPLAIN](evidence/issue68-71-mysql-explain.txt) 和 [EXPLAIN SQL](evidence/issue68-71-mysql-explain.sql)。
- 原施工全量 Maven：899 项，896 通过、3 跳过、0 失败/错误，见 [完整日志](evidence/issue68-71-full-test.txt)。后续 #3 的历史全量 Maven 为 972 项，968 通过、4 跳过、0 失败/错误，见 [#3 验证报告](issue3-ocr-transaction-verification.md)。

## 保留和未验证范围

原施工工作区 `D:\学习用\大二下\小学期-issue68-71-comprehensive-query-scope` 在本轮只读检查时不存在，未对其移动、删除、reset 或 prune，不能据此推断清理经过。原施工报告记载隔离 MySQL 实例关闭后保留数据目录；本轮只读确认 `%TEMP%\population_mysql84_issue6871_71879d0412` 目录存在，仓库中上述日志保留，未检查其数据库内容或改动任何原始数据和日志。

仍未验证：生产规模数据性能、大量迁移历史的聚合成本、`%keyword%` 全表扫描风险、真实生产环境部署、浏览器端完整人工回归、跨数据库字符集和排序规则边界，以及未明确授权的字段级特殊需求。

## 本轮账本门禁记录

以指定基线主表为对照：编号 1–86 连续且无重复，仅 #68、#71 行变更，原级别和摘要保持，其他 84 行及历史核销附表保持原样。六种状态统计：`FIXED_VERIFIED=44`、`CODE_CANDIDATE=4`、`FRONTEND_HANDOFF=21`、`OPEN=6`、`FIXED_NOT_VERIFIED=4`、`NOT_REPRODUCED=7`，合计 86。本文引用的仓库内文档和证据路径存在；生产源码和测试无改动，`git diff --check` 通过。上述门禁是本轮文档检查，并非新的业务测试。
