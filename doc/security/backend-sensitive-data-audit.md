# Backend V1 敏感数据审计

| 数据 | 存储 | 普通响应 | 完整查看 | 日志/Redis |
|---|---|---|---|---|
| 身份证、手机号、地址 | MySQL | 默认脱敏 | `sensitive-data:view-full` | 审计再次清洗；禁止缓存 |
| 通用证件号、居住证号 | MySQL | 掩码 | 独立业务权限及完整查看权限 | 禁止缓存 |
| password_hash | MySQL BCrypt | 永不返回 | 无 API | 不写日志/Redis |
| JWT | 客户端持有 | 登录时一次返回 | — | Redis 仅保存随机 jti 标记 |
| 材料、上传路径 | 文件系统+元数据 | 路径不返回 | 授权下载 | 日志仅记录动作 |
| 导出路径 | 文件系统+元数据 | 路径不返回 | 授权下载 | 有哈希、下载和过期审计 |
| 重点人口 | MySQL | 专项权限、默认脱敏 | 专项授权 | 不缓存完整详情 |

`SensitiveDataMaskingService.auditDetail` 清理身份证、手机号、Bearer Token 和 password 片段；统一异常响应不回显 SQL、内部路径或密钥。Redis 允许的业务值仅限参考数据和聚合统计。仓库环境变量样例只含占位符，演示密码仅用于本地种子数据和测试，不得复用于部署。
