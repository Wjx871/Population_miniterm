# S2 受保护远程统计派生摘要

## 状态

新增接口，默认关闭。需以 `S2_REMOTE_ENABLED=true` 或 `population.s2.remote.enabled=true` 启用；远程 RMI 主机固定为 `127.0.0.1`，Registry 与对象端口默认为 1199、1200。

## `GET /api/s2/statistics/aggregate`

- 认证：Bearer JWT。
- 权限：`statistics:view`。
- 请求体：无。客户端不能提交统计计数或数据范围。
- `X-Trace-Id`：可选；仅接受 1—64 位 ASCII 字母、数字、点、下划线或连字符。缺失或格式不符时 Java 生成 UUID。响应头和响应体回传实际使用的 traceId。

主后端在当前请求的安全上下文中调用 `StatisticsService.getSummary()`，沿用现有 `DataScopeCriteria.current()` 与 Mapper 过滤，再向本机 RMI 进程发送仅含汇总计数的 DTO。未启用时该 Controller 不注册；现有 `/api/statistics/**` 契约不变。

### 成功响应

外层为现有 `ApiResponse<T>`。下列数字仅为结构示例，不代表数据库实测结果：

```json
{
  "code": 200,
  "message": "ok",
  "data": {
    "schemaVersion": "1.0",
    "requestId": "s2-7a2f9e88c011",
    "traceId": "http-s2-demo-001",
    "residentCount": 1000,
    "floatingRegistrationCount": 17,
    "activeKeyPopulationCount": 21,
    "activeHouseholdCount": 380,
    "floatingRegistrationToResidentRatio": 0.0170,
    "activeKeyPopulationToResidentRatio": 0.0210,
    "residentToActiveHouseholdRatio": 2.6316
  },
  "timestamp": 1793700000000
}
```

比率保留 4 位小数、使用 `HALF_UP`。分母为 0 时该比率为 `null`；计数必须在 `0..Long.MAX_VALUE`。这些比率不表示人口构成百分比；当前 SQL 的计数对象及数据范围规则并不保证分子属于 residentCount。

### 故障响应

| HTTP | `message` 错误码 | 说明 |
|---:|---|---|
| 401 | 现有认证错误 | 缺少或无效 Bearer JWT。 |
| 403 | 现有权限错误 | 账号没有 `statistics:view`。 |
| 404 | — | 默认关闭时接口未注册。 |
| 503 | `REMOTE_UNAVAILABLE` | RMI Registry/服务不可达。 |
| 503 | `REMOTE_TIMEOUT` | RMI 连接/读取超时。 |
| 503 | `REMOTE_FAILURE` | 其他远程调用失败。 |
| 400 | 校验错误码 | 远程契约拒绝不支持的版本、关联 ID 或负数计数。 |

失败时响应头仍返回本次 `X-Trace-Id`。远程故障不回退为本地伪造统计，也不改变已有统计接口。
