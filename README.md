# Population Miniterm

人口数据库管理系统后端，基于 Spring Boot、Spring Web、Spring Data JPA 和 MySQL。

## 环境要求

- JDK 17+
- MySQL 8+

## 小组开发数据库约定

平时开发时，每个人连接自己的本地 MySQL 数据库。大家共享项目代码和 SQL 文件，不共享某一个人的数据库。

统一数据库名：

```text
population_miniterm
```

数据库初始化脚本：

```text
doc/database/population_miniterm.sql
```

每位组员拿到项目后，先在自己的 MySQL 中执行该 SQL 文件，然后按自己的本机账号密码启动项目。

## 启动方式

PowerShell 示例：

```powershell
$env:JAVA_HOME="D:\ASUS"
$env:Path="$env:JAVA_HOME\bin;C:\Windows\System32\WindowsPowerShell\v1.0;$env:Path"
$env:DB_USERNAME="root"
$env:DB_PASSWORD="你的MySQL密码"
.\mvnw.cmd spring-boot:run
```

如果你的数据库名、地址或端口不同，可以额外设置：

```powershell
$env:DB_URL="jdbc:mysql://localhost:3306/population_miniterm?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai"
```

默认服务地址为：

```text
http://localhost:8080
```

## IDEA 配置

打开运行配置 `PopulationMinitermApplication`，在 Environment variables 中填入：

```text
DB_USERNAME=root;DB_PASSWORD=你的MySQL密码
```

如果 IDEA 找不到 JDK，在 Project SDK 中选择：

```text
D:\ASUS
```

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
