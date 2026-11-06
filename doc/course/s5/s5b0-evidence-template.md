# S5-B0 联调证据与问题台账模板（冻结候选）

日期：2026-11-06。事实基线：`9f9c13727c552f031b97862dea1595db7e643453`。

本模板供 [Runbook](s5b0-integration-runbook.md)真实执行时逐项复制填写；当前为 **CODE_CANDIDATE / 冻结候选**，未记录任何真实PASS。所有未知字段/接口为 **PENDING_CONFIRMATION**，真实执行为 **NOT_VERIFIED**；必要实现或环境缺失另记 **BLOCKED**。模板存在、文件存在、健康200、Redis PING、已有H2/Mock通过都不能填成真实联调PASS。

## 1. 判定、证据层级与保密要求

| 字段 | 允许值/规则 |
|---|---|
| 验收结果 | `PASS`：本Test ID在声明环境、范围、版本下有完整原始证据；`FAIL`：已执行且违反预期；`BLOCKED`：有明确必要依赖缺失；`NOT_VERIFIED`：未执行或证据不足 |
| 确认状态 | `PENDING_CONFIRMATION` / 已确认（必须有确认人、时间及证据）；已确认不能由模板作者代填 |
| 证据层级 | `STATIC`、`H2`、`MOCK`、`HTTP_STUB`、`SYNTHETIC_DEMO`、`REAL_ENV`；同一结果混合环境时逐组件注明 |
| 执行状态 | `NOT_RUN` / 已执行（时间）；Maven未重跑时写NOT_RUN，不沿用历史730项作本轮结论 |
| 原始证据 | 受控存储位置、采集时间、SHA-256、脱敏方式、访问责任人；公开/提交版本只留安全索引 |
| 会话/凭据 | 不记录密码、JWT/JTI、Authorization/Cookie、服务token、密钥、敏感连接串或其可复用摘要；使用随机安全会话别名 |
| 个人与文件数据 | 仅合成人员/材料；日志/截图去除证件原文、完整地址、宿主路径、对象键、原始消息体；保留足够核验的安全关联 |

真实RabbitMQ+Mock分析只能分别证明Broker与Mock组合；真实政策服务不能替代真实文档分析；真实单库不能替代主从路由。输出 `PASS` 时必须同时写清证据层级、覆盖范围和不能证明的事项。

## 2. 运行批次与环境确认模板

```text
runId：待填
文档版本/Runbook版本：待填
候选状态：CODE_CANDIDATE；实际环境契约确认：PENDING_CONFIRMATION
执行状态：NOT_RUN
真实验收结果：NOT_VERIFIED
操作起止时间（含时区；UTC及Asia/Shanghai）：待填
Git完整commit SHA：待填（不得仅填“最新”）
事实基线：9f9c13727c552f031b97862dea1595db7e643453
参与服务及版本/镜像标识：待填
环境性质（隔离/本地/云；不得以本地冒称上云）：待填
隔离数据库安全别名/归属/版本/迁移盘点：待填
数据库隔离及迁移授权记录：PENDING_CONFIRMATION；本模板不授权执行迁移
合成数据夹具版本/安全标识/文件SHA-256：待填
合成账号类别及角色/权限/DataScope/部门/区划：待填（不得记录密码）
A instanceId/独立JVM身份/代码SHA：待填
B instanceId/独立JVM身份/代码SHA：待填
A/B与Nginx受控端点安全别名：PENDING_CONFIRMATION
Nginx配置版本/分流证据/非粘滞说明：PENDING_CONFIRMATION
14项环境契约确认记录位置：待填
A/B配置一致性安全比较结果位置：待填（不得附原始环境或密钥）
主从路由实现版本/专属配置名称/物理节点观察方式：PENDING_CONFIRMATION
Redis版本/一致端点/database/prefix比较/ACL/noeviction：PENDING_CONFIRMATION
Redis持久化/备份/复制策略/可接受丢失窗口：PENDING_CONFIRMATION
Redis撤销历史完整性确认/旧JWT作废策略：PENDING_CONFIRMATION
RabbitMQ版本/VHOST/队列/DLQ观测方案：PENDING_CONFIRMATION
Python Document Analysis实际客户端/端点/schema/认证/读取方案：PENDING_CONFIRMATION
Python Policy Query实际端点/schema/认证/来源版本：PENDING_CONFIRMATION
普通上传/AI私有暂存/正式材料/导出共享资源与生命周期：PENDING_CONFIRMATION
Web/JavaFX实际环境、页面和受保护聚合API：PENDING_CONFIRMATION
日志/指标采集方式、时间窗及校时偏差：PENDING_CONFIRMATION
允许操作/负载/故障窗口/停止阈值：PENDING_CONFIRMATION
故障前恢复点与备份安全索引：待填
环境主责确认人/时间/确认范围/证据：PENDING_CONFIRMATION
Java/Python/前端负责人分别确认记录：PENDING_CONFIRMATION
基础设施停止/恢复负责人：PENDING_CONFIRMATION
Python停止/恢复负责人：PENDING_CONFIRMATION
业务一致性复核负责人：PENDING_CONFIRMATION
拟清理责任人与“先列清单再许可”的记录：PENDING_CONFIRMATION
阻断依赖及处理计划：待填；无HTTP文档分析客户端/路由器/共享卷时必须BLOCKED
```

## 3. 单项Test ID完整模板

每一个Phase步骤、A1场景、A2用例、文件/消息用例、39行路由、故障/恢复及负面权限请求分别建记录。一个Test ID仅有A实例结果时不能写双实例PASS。

```text
Test ID：待填（例如选用Runbook中的A1-01，不把例子当本次结果）
runId / Phase / 关联用例：待填
标题/验证目标：待填
原合同/源码/39行矩阵标题与位置：待填
环境版本：待填
Git完整commit SHA：待填
A/B instanceId及实际处理JVM：待填
参与服务：待填
证据层级：待填（STATIC/H2/MOCK/HTTP_STUB/SYNTHETIC_DEMO/REAL_ENV）
角色/测试账号类别：待填（不得记录密码）
权限/DataScope/部门/区划及前置状态：待填（仅合成夹具）
准备数据/夹具版本/文件哈希：待填
必要依赖及其确认记录：PENDING_CONFIRMATION
请求（方法/路由/安全参数摘要；不含密码、JWT、token）：待填
目标安全别名/路由入口（直达A/直达B/Nginx）：待填
重复执行与幂等key的安全别名/是否同规范化请求：待填（不记录真实确认令牌）
预期结果（成功/冲突/权限拒绝/无副作用等逐条）：待填
实际结果：未执行，待填
HTTP code：未执行，待填
业务 code/message安全类别：未执行，待填
文件响应业务code：适用时填“不适用”，不得编造JSON结果
traceId / taskId / eventId / requestId：待填（按实际有无记录）
applicationId / approvalId / materialId的合成映射：待填（两种ID不得混用）
连接到的真实数据库节点：未验证，待填
实际事务active/readOnly/传播及Mapper/SQL节点证据：未验证，待填
主从位点/延迟与路由决定：未验证，待填
业务前后状态/行数/版本/提交或回滚：未验证，待填
Redis严格读写确认/故障类别/TTL/完整性证据：适用时待填
Broker confirm/return/重试/DLQ/Outbox证据：适用时待填
跨实例文件授权/哈希/共享对象安全别名：适用时待填
负面权限对照与无他人内容/无业务写入证明：待填
操作时间（请求/提交/故障/恢复，含时区）：待填
脱敏日志与截图位置：待填
原始证据编号/受控位置/SHA-256/保管人：待填
执行人/负责人/复核人：PENDING_CONFIRMATION
实际验收结果：NOT_VERIFIED
缺陷编号：待填（BUG-xxx / INT-xxx；未发生不伪造编号）
停止条件是否触发/隔离动作：未执行，待填
故障恢复步骤（实际执行而非只抄计划）：未执行，待填
恢复后旧会话/业务/权限/文件回归：未执行，待填
恢复负责人/确认时间/允许重开流量证据：PENDING_CONFIRMATION
清理清单/影响/恢复点/用户明确许可/执行人：未执行，待填
结论覆盖范围与限制：待填；不得把H2/Mock或健康200扩展成真实验收
```

## 4. 39行路由逐行记录模板

完整矩阵以 [S5-A2第2节](s5a2-transaction-cache-boundaries.md#2-业务事务与-mapper-路由矩阵)为唯一维护源，本文件不重抄。按照该版本原39行顺序建立 `ROUTE-01`—`ROUTE-39` 索引，逐行写原业务标题及真实证据；本模板留空不等于39行已执行。

```text
路由记录ID：ROUTE-待填（01—39）
原矩阵Git SHA/业务标题/行顺序：待填
关联Test ID/runId：待填
建议PRIMARY或REPLICA_ELIGIBLE及允许条件：待填（引用原矩阵）
静态Java声明/自调用/传播边界：待填，证据层级STATIC
本次实际事务active/readOnly/autoCommit/传播：未验证，待填
请求/instanceId/traceId：待填
Mapper方法/安全SQL类别/是否FOR UPDATE：待填
业务实际连接的安全标识：未验证，待填
同一连接所到物理server_id/read_only/节点别名：未验证，待填
节点身份采集方法/与本次SQL关联证明：未验证，待填
主从位点/复制延迟/延迟预算：未验证，待填
写后读取/权限加载/事务外幂等涉及节点：未验证，待填
副本故障的整次回主或明确失败/事务结束证据：未验证，待填
主库故障时无改投从库/无部分写入证据：未验证，待填
实际结果/HTTP与业务code：未执行，待填
原始证据位置/哈希/采集时间/保管人：待填
确认状态：PENDING_CONFIRMATION
真实验收结果：NOT_VERIFIED
BLOCKED原因/负责人/处理计划：待填
```

**物理证明必须与业务同一连接关联**；另开连接查节点、只观察@Transactional、Mock路由器或H2单库均不能填真实节点PASS。命中缓存而没有SQL时如实写“本请求无数据库SQL”，另安排经批准的缓存miss来验节点。

## 5. 覆盖索引模板

```text
Phase A/B/C/D/E/F：分别建立本节第3部分的完整记录，当前均NOT_VERIFIED。

S5-A1：
A1-01 A登录/B验证：NOT_VERIFIED
A1-02 A退出/B拒绝：NOT_VERIFIED
A1-03 B退出/A拒绝：NOT_VERIFIED
A1-04 Redis停止/A+B受保护请求503：NOT_VERIFIED
A1-05 完整撤销数据恢复：NOT_VERIFIED
A1-06 写入失败不虚假登出成功：NOT_VERIFIED
A1-07 认证失败/超时fail-closed：NOT_VERIFIED
A1-08 登录预检失败不签发成功会话：NOT_VERIFIED
A1-09 单实例下线/另一实例接管：NOT_VERIFIED

S5-A2至少十三类（不能替代39行逐行物理记录）：
A2-01 PRIMARY写事务：NOT_VERIFIED
A2-02 REPLICA_ELIGIBLE统计：NOT_VERIFIED
A2-03 写后即时读主：NOT_VERIFIED
A2-04 SELECT FOR UPDATE走主：NOT_VERIFIED
A2-05 提交/审批/执行走主：NOT_VERIFIED
A2-06 事务外幂等走主：NOT_VERIFIED
A2-07 副本延迟与写后读：NOT_VERIFIED
A2-08 副本断连安全回主/失败：NOT_VERIFIED
A2-09 字典管理员/普通用户隔离：NOT_VERIFIED
A2-10 统计DataScope跨实例隔离：NOT_VERIFIED
A2-11 普通缓存故障回源：NOT_VERIFIED
A2-12 旧值回填/提交回滚/失效失败：NOT_VERIFIED
A2-13 KEYS实际规模性能：NOT_VERIFIED
ROUTE-01—ROUTE-39：全部NOT_VERIFIED；实际路由实现缺失时相应条目标BLOCKED。

共享文件与消息：
FS-01 普通正式材料A上传/B读取与越权对照：NOT_VERIFIED
FS-02 AI暂存A上传/B继续确认：NOT_VERIFIED
FS-03 AI绑定A确认/B正式下载与AI关闭：NOT_VERIFIED
FS-04 A导出/B按授权下载：NOT_VERIFIED
FS-05 身份证影印本元数据/共享文件/归属：NOT_VERIFIED
FS-06 A任务/B进度与越权对照：NOT_VERIFIED
FS-07 A/B并发幂等：NOT_VERIFIED
FS-08 Outbox/confirm/重试/DLQ跨消费者：NOT_VERIFIED
FS-09 文件故障与无内容泄漏：NOT_VERIFIED

其他必要对照：
传统人工入口不依赖Python/MQ：NOT_VERIFIED
AI只建DRAFT、材料PENDING、独立核验/审批/execute：NOT_VERIFIED
旧会话/错类候选/越权目标户/无确认或执行权限：NOT_VERIFIED
停机恢复后原流程复测：NOT_VERIFIED
真实Web页面与JavaFX/受保护运维接口：NOT_VERIFIED；接口缺失子项BLOCKED
```

上述为覆盖清单，实际原始证据索引未填，不得将其作为已完成打勾表。

## 6. 故障、恢复与Redis历史完整性模板

```text
故障ID / 关联Test ID / runId：待填
服务/故障类别：待填
环境主责/服务主责确认人、时间、批准范围：PENDING_CONFIRMATION
允许注入的具体对象/影响/窗口/停止阈值：PENDING_CONFIRMATION
故障前基线/恢复点/备份安全索引：待填
故障前已撤销/未撤销会话安全别名及原状态：待填（不记录JWT/JTI）
计划注入方法/具体命令确认记录：PENDING_CONFIRMATION
实际注入动作/时间/执行人：未执行，待填
发现时间/异常指标/日志/请求错误：未执行，待填
traceId/taskId/eventId及实例/节点：待填
是否触发停止/实际隔离流量动作：未执行，待填
实际业务状态/是否无部分提交：未验证，待填
Redis数据是否完整/依据（不能用PING或KEY_ABSENT代替）：未验证，待填
持久化/复制/恢复点及可接受丢失窗口：PENDING_CONFIRMATION
若历史丢失或未知，旧JWT作废方案、覆盖全部实例/最长生命周期：PENDING_CONFIRMATION
密钥轮换等具体变更的对象/影响/单独许可记录：未执行，待填
实际恢复步骤、执行人、起止时间：未执行，待填
恢复后旧已撤销会话/未撤销会话/新登录退出结果：未验证，待填
恢复后RBAC/DataScope/主库业务/文件/队列回归：未验证，待填
恢复负责人及业务负责人复核/重开流量时间：PENDING_CONFIRMATION
原始证据位置/哈希与保管人：待填
实际结果：NOT_VERIFIED
BUG-xxx/INT-xxx：待填
```

当前strict不会自动发现或修复撤销历史丢失。旧备份恢复、重启/切换/回滚、FLUSH、驱逐或持久化缺失必须保留恢复隔离和旧JWT失效策略；全实例统一轮换JWT_SECRET或等待全部旧JWT到期仅是待确认选项，不是本文已经授权或执行的动作。

## 7. 压测与最终演示模板

```text
Test ID/runId/环境版本/Git SHA：待填
拓扑（本地或云）/服务版本/实例数/资源：待填
合成数据量/键规模/查询范围/文件规模：待填
并发/速率/预热/持续时长/客户端版本：PENDING_CONFIRMATION
比较组（缓存/路由/双实例）及唯一变化因素：待填
停止阈值/允许负载/主责确认：PENDING_CONFIRMATION
真实采样窗口/采样源/未采集字段：待填（未采集不得填0）
原始延迟分布/P50/P95/吞吐/错误率：未测量，待填
CPU/内存/连接池/复制延迟/队列积压/KEYS耗时：未测量，待填
故障注入/发现/定位/恢复时间线：未执行，待填
恢复后的传统流程/AI显式确认/权限/文件回归：未验证，待填
Web/JavaFX页面、引用/模式/任务状态截图索引：待填
原始测量位置/哈希/分析口径/负责人：待填
结果：NOT_VERIFIED
限制/未完成项：待填（不编造提升百分比，不把本地演示称上云）
```

## 8. BUG-xxx / INT-xxx问题台账模板

```text
问题编号：待填（BUG-xxx或INT-xxx，先查现有台账避免覆盖）
发现人/发现时间：待填
主责人/协作人/复核人：PENDING_CONFIRMATION
关联runId/Test ID/Phase/Git SHA：待填
环境版本、A/B实例及参与服务：待填
角色/权限/DataScope与合成数据前置条件：待填
场景与最小复现步骤（方法/路由/安全输入摘要）：待填
预期行为：待填
实际行为/HTTP code/业务code：待填
影响范围/风险/是否触发停止隔离：待填
traceId/taskId/eventId/物理DB节点：待填
脱敏日志/截图/原始证据位置和哈希：待填
原因：待填（未定位写未知，不猜测已证实根因）
处理方案/责任边界/所需确认与授权：待填
修复commit SHA/配置版本：未修复，待填
回归Test ID/真实环境层级/实际结果：NOT_VERIFIED
恢复/回退动作与负责人：待填
当前问题状态/结项依据/复核时间：待填（不得由空模板填FIXED）
```

## 9. 原始证据索引与清理申请模板

```text
Evidence ID：待填
关联Test ID/runId/BUG-xxx或INT-xxx：待填
证据类型/层级/采集方法/时间/执行人：待填
受控原始位置及访问保管人：待填（不得公开敏感连接路径）
文件名安全别名/大小/SHA-256：待填
脱敏副本位置/脱敏内容说明：待填
覆盖请求/实例/节点/时间窗/测量口径：待填
保存期限与责任人：PENDING_CONFIRMATION

清理申请（无许可不得执行）：
拟操作文件/数据/消息确切清单：待填
归属及用途核实：待填（不明对象保留）
操作类型（删除/覆盖/移动等）、原因、影响范围：待填
恢复点/保留原始证据/回退方式：待填
执行责任人/复核人：PENDING_CONFIRMATION
用户明确许可记录/时间/许可范围：未取得，待填
实际执行/结果：未执行
```

## 10. 最终汇总模板与本轮边界

```text
S5-B0文档/工具：CODE_CANDIDATE
Offline预检本次runId/实际结果：待实际运行填写，不由本模板代填
脚本自身测试/证据层级：待实际运行填写；内存mock不算真实服务
本批次相关既有测试：待填；未执行填NOT_RUN，已执行另附命令/时间/结果
本批次完整Maven回归：待填；未执行填NOT_RUN，历史730项不是本批次执行证据
真实Redis/Nginx双实例：NOT_VERIFIED
真实MySQL主从路由：NOT_VERIFIED
真实RabbitMQ：NOT_VERIFIED
真实Python服务（政策/文档分别记）：NOT_VERIFIED
真实前端联调：NOT_VERIFIED
39行物理节点记录已填数量/缺口：待真实执行逐行填写，不由空模板代填数量
BLOCKED依赖/负责人/下一步：待填
PENDING_CONFIRMATION字段/接口与确认人：待填
真实环境执行前主责确认：PENDING_CONFIRMATION
#64：OPEN（完整真实双JVM/Redis/Nginx分流/故障恢复证据齐备后才可提议关闭）
#3：OPEN
#33：OPEN
#21：FIXED_VERIFIED
独立复核人/时间/适用范围/剩余限制：待填
```

状态汇总中的初值用于明确“尚未执行”。后续只能在保留批次、命令、环境、时间与原始证据后更新对应记录；不得把空模板、STATIC/H2/Mock历史结果复制成REAL_ENV PASS。本轮完成后停止，不自动开展S5-B1、真实部署/故障注入或S6。
