# Population Miniterm

人口数据库管理系统后端，基于 Spring Boot、Spring Web、Spring Data JPA 和 MySQL。

## 环境要求

- JDK 17+
- MySQL 8+

## 运行方式

先创建数据库：

```sql
CREATE DATABASE population_miniterm DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

设置数据库连接信息后启动：

```powershell
$env:DB_URL="jdbc:mysql://localhost:3306/population_miniterm?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai"
$env:DB_USERNAME="root"
$env:DB_PASSWORD="你的数据库密码"
.\mvnw.cmd spring-boot:run
```

默认服务地址为 `http://localhost:8080`。

## 居民人口接口

- `GET /api/residents`：分页查询居民，可选 `keyword`
- `GET /api/residents/{id}`：查看单个居民
- `POST /api/residents`：新增居民
- `PUT /api/residents/{id}`：更新居民
- `DELETE /api/residents/{id}`：删除居民

新增示例：

```json
{
  "name": "张三",
  "gender": "MALE",
  "birthDate": "1999-01-01",
  "idCardNumber": "110101199901010011",
  "phoneNumber": "13800138000",
  "province": "北京市",
  "city": "北京市",
  "district": "东城区",
  "address": "示例地址",
  "active": true
}
```
