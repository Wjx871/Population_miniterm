# 2026-11-01 邑循后端缺陷登记

范围：基于基线 `caf3f9aabd89467df78bf3dbb0eb1ad6f5b0ddbc` 的 H2 回归、定向重跑及静态契约核对。未修改生产代码。MySQL 运行验证 BLOCKED，因此标注为疑似的项目不能视为已完成生产数据库确认。

## 优先级汇总

| 状态 | 数量 | 项目 |
|---|---:|---|
| 已确认 | 1 | P0 OCR 影印本详情接口泄露未脱敏字段 |
| 疑似 | 2 | P2 普通导出文件归属绕过；P2 Fresh 初始化 viewer 范围不一致 |
| 待确认需求 | 1 | `QUERY_VIEWER` 的 ALL 范围是否可以跨用户下载普通脱敏导出 |
| P1 | 0 | — |

## BUG-20261101-01 — OCR 影印本详情返回完整身份证号和地址

- **状态 / 优先级：** 已确认，P0。
- **模块 / 轮次：** 身份证影印本、权限与隐私；第二、三轮。
- **稳定性：** H2 单类测试和最终全量回归均稳定复现；不是外部服务或随机时序问题。
- **前置条件：** 数据库存在 OCR 状态为 `SUCCESS` 的影印本；调用者登录为 viewer，拥有 `population:view`，没有 `sensitive-data:view-full`。
- **复现步骤：**
  1. 使用本地 stub 上传测试图片，令 OCR 返回合成姓名、合成证件号 `110105199001010010` 和详细地址 `测试地址`。
  2. 以 viewer 登录，调用 `GET /api/persons/idcard-image/{imageId}`。
- **预期行为：** 读取权限不包含 `sensitive-data:view-full` 时，完整身份证号和详细地址应拒绝返回或按系统脱敏规则处理。
- **实际行为：** HTTP 200，JSON 包含 `ocrIdcardFull=110105199001010010`、`ocrAddress=测试地址`。测试使用合成数据，没有真实个人信息。
- **最小复现测试：** `src/test/java/com/wjx871/population/person/idcard/Phase14PersonIdCardOcrIntegrationTest.java` 中 `viewerCannotReadUnmaskedOcrFieldsFromImageDetails`。最终全量 Surefire 失败信息：`not to contain [身份证号, 测试地址] but found both`。
- **涉及生产代码：** `PersonIdCardImageController.get` 仅要求 `population:view`；`PersonIdCardImageService.getById` 按 ID 读取；`PersonIdCardImageView.from` 在 OCR 成功时无条件填充完整证件号，且详情字段未按调用者权限脱敏。`population:view` 对 viewer 开放，H2 中 viewer 的数据范围为 ALL。
- **可能根因：** 上传回填和详情查询共用同一个 `PersonIdCardImageView`。上传表单需要识别字段回填，但详情路由没有按用户权限区分可返回字段，也没有做对象范围/归属复核。
- **是否真实生产缺陷：** 是。通用 Definition of Done 要求敏感字段默认脱敏、完整信息使用独立权限；viewer 不具备该权限。DTO 注释和前端上传流程确实消费 `ocrIdcardFull`，但这不足以证明只读详情接口可向 viewer 暴露完整身份信息。
- **建议：** 将上传回填响应与影印本只读详情拆分；只读详情应用敏感字段权限和对象数据范围，未授权时隐藏或脱敏完整身份证号与详细地址。不要在该测试分支修复生产代码。

## BUG-20261101-02 — viewer 可下载其他用户生成的普通脱敏导出

- **状态 / 优先级：** 疑似，P2；下载策略需按下列待确认需求确认。
- **模块 / 轮次：** 导出、下载权限；第一、三轮。
- **稳定性：** 基线全量和独立单测均复现；最终全量仍复现。
- **前置条件：** population 创建一份 `NORMAL_MASKED` 导出；viewer 拥有 `data:export:normal`，但不是请求人或执行人；viewer 的数据范围为 ALL。
- **复现步骤：** 创建普通人口脱敏导出，然后用 viewer 调用 `GET /api/exports/{id}/download`。
- **预期行为：** 现有 `otherNormalUserCannotDownload` 用例预期 403；Phase 06 权限审计文档描述请求人、执行人或范围管理员可下载。viewer 是 `QUERY_VIEWER`、L1，并非 `SYSTEM_ADMIN`。
- **实际行为：** 下载接口返回 200 和文件内容。
- **最小复现测试：** `src/test/java/com/wjx871/population/export/Phase06ExportAuditIntegrationTest.java` 的 `otherNormalUserCannotDownload`。最终全量失败摘要：`expected:<403> but was:<200>`。
- **涉及生产代码：** `ExportService.assertDownload` 将 `u.dataScope() == DataScope.ALL` 直接作为 `admin`，因此 V4_012 将 viewer 改为 ALL 后绕过文件请求人/执行人检查。
- **可能根因：** 数据可见范围和管理员身份混用。普通导出内容强制脱敏，且 viewer 可自行创建普通导出；实际损害范围和审计影响需结合业务策略确认。
- **是否已确认生产 Bug：** 尚未完全确认。行为本身稳定，已有测试偏向所有权限制；但旧测试早于 V4_012，且“范围管理员”是否包括任何 ALL 用户的契约没有明确到 viewer。
- **建议：** 先确认 ALL 的 viewer 是否属于普通导出文件的“范围管理员”。若不属于，应改成独立管理员/所有权判断并保持 403 测试；若允许，应补充明确设计文档和 viewer 跨用户下载测试。

## BUG-20261101-03 — Fresh 初始化结果可能保留 viewer=DEPARTMENT

- **状态 / 优先级：** 疑似，P2；MySQL runtime 未验证。
- **模块 / 轮次：** 数据初始化、数据范围；第四轮静态审计。
- **证据：** `doc/database/migrations/V4_012_dashboard_viewer_scope.sql` 明确将 `QUERY_VIEWER` 更新为 `ALL`；README 和 H2 fixture 也为 ALL。`doc/database/population_miniterm.sql` 的 canonical role seed 及 ASCII repair seed 仍将 viewer 写为 `DEPARTMENT`。`scripts/windows/init_database.ps1 -Mode Fresh` 仅导入主 SQL，不执行 V4_012。
- **影响：** 全新安装可能与当前 viewer 全量只读设计及 H2 行为不一致，影响 dashboard KPI、跨部门只读结果和测试/生产一致性。
- **稳定性 / 真实生产 Bug：** 静态路径确定；没有连接真实 MySQL，Fresh 执行后的实际角色值未验证。因此登记为疑似，不冒称已在生产数据库复现。
- **建议：** 在独立 MySQL 库验证 Fresh 结果；保持主 SQL 与 V4_012 的最终状态一致，或确保 Fresh 明确执行对应迁移，并增加角色范围检查。

## REQ-20261101-01 — viewer 的 ALL 范围是否覆盖跨用户导出文件

- **状态：** 待确认需求。
- README、V4_012 和 H2 fixture 确认 viewer 的数据范围为 ALL；V4_012 同时称其为只读统计角色，写入 API 仍受权限控制。普通导出权限列表授予 viewer `data:export:normal`，但文件下载所有权说明与 viewer 的 ALL 范围交叉处未明确。
- 不应通过改动 403 断言或将 ALL 等同系统管理员来掩盖这一策略选择。需确定“ALL”仅表示可查看/导出业务数据，还是包含下载其他用户的普通导出文件。

## 已核对的旧断言与文档不一致

基线中的两个 viewer 断言预期 `DEPARTMENT`，但 V4_012 迁移、README、H2 fixture 和 Phase 08 viewer 查询测试共同表明当前设计已改为 ALL。已在测试分支更新这两条旧断言为 ALL 并验证认证类 16 项全部通过。旧权限矩阵和端到端报告仍保留旧范围表述；主 SQL Fresh seed 另列为 `BUG-20261101-03`，未通过人工改断言规避。
