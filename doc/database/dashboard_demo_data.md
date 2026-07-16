# 大屏批量演示数据

`scripts/data/generate_dashboard_demo_data.py` 只生成 SQL，不会自行连接或修改数据库。生成内容均为固定编号、虚构身份和地址的演示数据，用于验证数据大屏真实接口。

只允许导入全新、可丢弃的演示库。推荐流程：

```powershell
powershell -ExecutionPolicy Bypass -File scripts/windows/init_database.ps1 -Mode Fresh -Database population_miniterm_demo
python scripts/data/generate_dashboard_demo_data.py --database population_miniterm_demo --output tmp/dashboard_demo_data.sql --people 1200
Get-Content -Raw -Encoding utf8 tmp/dashboard_demo_data.sql | mysql --protocol=TCP --host=localhost --port=3306 --user=root --default-character-set=utf8mb4 population_miniterm_demo
```

如 MySQL 启用了密码，请仅在当前终端会话中设置 `MYSQL_PWD` 后执行导入命令，勿把密码写入仓库文件。

生成集包括 1,200 名人员、720 条有效户籍登记、480 条流动人口记录、350 张有效居住证（75 张在 30 天内到期）、90 条重点人口、685 条业务申请与审批，以及近 30 天的迁入迁出趋势数据。

不要导入课程库或任何真实业务库。若要让本地后端使用该库，只调整本机 `start.local.env` 的 `DB_URL`；该配置文件已被 Git 忽略，禁止提交账号或密码。
