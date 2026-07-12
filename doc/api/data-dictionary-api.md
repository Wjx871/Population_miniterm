# 数据字典 API

`dictionary:view` 可访问：

- `GET /api/dictionaries`：支持 `dictionaryType/dictionaryCode/keyword/status/page/size`；
- `GET /api/dictionaries/{type}`；
- `GET /api/dictionaries/{type}/{code}`。

普通用户只能看到 `ENABLED` 项；拥有 `dictionary:manage` 的管理员可查看全部，并可调用 `POST /api/dictionaries`、`PUT /api/dictionaries/{id}`、`POST .../enable`、`POST .../disable`。创建字段为 `dictionaryType/dictionaryCode/displayName/sortNo`，更新与启停使用 `version`。类型与编码组合唯一，重复或版本冲突返回 409，参数错误返回 400，越权返回 403。

当前配置化种子覆盖民族、家庭关系、家庭户类型、通用证件类型、迁移/注销/流动居住原因及重点人口类型。审批和业务状态等安全关键状态继续由代码内受控枚举管理；禁用项不影响历史显示，但不能用于新证件业务。
