# 流动人口与居住证生命周期设计

## 模型边界

`person` 是身份主档；`residence` 是本地户籍；`floating_population` 是人员在当前行政区域的居住登记。流动登记不改变户籍，也不建立家庭成员关系。`floating_registration_application` 保存审批前的来源区划、当前地址、事由、证明和日期，审批通过后必须显式执行才产生正式登记。

`residence_permit_application` 统一承载首次申领、签注和注销的专业字段。`residence_permit` 是新居住证唯一业务来源；通用 `certificate` 只兼容历史记录。`residence_permit_log` 是只增不改的证件生命周期轨迹，`operation_log` 则记录谁通过哪个系统操作改变了数据，两者不能互相替代。

## 当前与历史

登记和证件均用 `status` 表达业务状态，用 `current_flag=1` 表示当前有效记录，结束后置为 `NULL`。MySQL 的 `UNIQUE(person_id,current_flag)` 允许多个 NULL，从而保证至多一个当前记录并保留多条历史。H2 测试使用等价约束验证语义。

## 状态机

- 登记：DRAFT → UNDER_REVIEW → APPROVED → COMPLETED；正式记录 ACTIVE → LEFT/CANCELLED/EXPIRED。
- 首次申领：批准后显式签发 ACTIVE 证件并写 ISSUE。
- 签注：批准后在配置窗口内延长有效期并写 ENDORSE。
- 注销：批准后置 CANCELLED、清除当前标志并写 CANCEL。
- 自动任务：过期证件置 EXPIRED 并写 EXPIRE；登记结束会同步失效 ACTIVE 居住证并写 REGISTRATION_CLOSED。

所有日期来自注入的 `Clock`。最短居住天数、证件期限、签注提前窗口、提醒窗口和 cron 由 `ResidencePermitProperties` 集中管理，不在 Controller 分散计算。

## 事务、并发和幂等

创建专业申请、登记执行、签发、签注、注销、关闭和单条到期处理分别处于事务边界。执行使用行锁、状态条件更新、版本检查、唯一申请约束以及人员当前标志唯一约束。生命周期日志、专业状态、通用申请状态和业务实体在同一事务提交。可替换事务钩子覆盖插入后、日志前后、状态更新后和到期更新后的回滚。

到期扫描只负责找候选项，每条记录由 `REQUIRES_NEW` 工作器处理，重复扫描因 ACTIVE 条件不会重复写日志；单条异常向上报告，不静默吞掉。

## 兼容与后续

历史 `certificate` 居住证保持只读，人工迁移必须逐条核验，不静默删除。未来重点人口模块应通过新的专业业务类型、专业详情表、材料服务、提交校验器和状态监听器接入，不复用居住证表表达无关业务。本阶段明确不提供补换领、真实卡面、二维码、电子签章或政务联网。
