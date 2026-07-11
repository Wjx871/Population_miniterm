# Deployment guide

## Requirements

- JDK 17
- MySQL 8.0 or 8.4 LTS with `utf8mb4`
- Node.js 20+ and npm with the committed dependency versions

## Database

For a new database, execute `doc/database/population_miniterm.sql`. For an upgrade, back up first and execute V4_001 through V4_006 in filename order. Use `scripts/verify-mysql.ps1` against one of the three explicitly allowed disposable test database names before production deployment. `doc/database/demo_data.sql` is optional and is only for course demonstrations.

## Environment variables

Required production values: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, and a random `JWT_SECRET` of at least 32 bytes. Optional controls include `SERVER_PORT`, `APP_UPLOAD_DIR`, `APP_UPLOAD_MAX_SIZE_MB`, `EXPORT_DIR`, export row limits and retention, residence-permit rule days and cron expressions. Never commit environment-specific values.

PowerShell example (placeholders only):

```powershell
$env:DB_URL='jdbc:mysql://127.0.0.1:3306/population_miniterm?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai'
$env:DB_USERNAME='population_app'
$env:DB_PASSWORD='<set outside source control>'
$env:JWT_SECRET='<random value of at least 32 bytes>'
$env:APP_UPLOAD_DIR='D:\population-data\uploads'
$env:EXPORT_DIR='D:\population-data\exports'
.\mvnw.cmd spring-boot:run
```

Build and start the frontend with `npm ci`, `npm run build`, or `npm run dev` under `frontend`. Set `VITE_API_BASE_URL` when the API is not served through `/api` on the same origin.

Upload and export directories must be writable by the application account, outside the web root, excluded from Git, and included in retention/backup policy. Back up MySQL before every migration and regularly test restore procedures. Default course accounts use `123456` only in local demo data and must be disabled or changed in any deployed environment.

Common failures: verify MySQL version and timezone, DB grants, `utf8mb4`, JWT length, directory permissions, reverse-proxy body limits, and that migrations were applied in order. Do not bypass foreign keys to work around invalid data.
