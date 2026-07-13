# Deployment guide

## Requirements

- JDK 17
- MySQL 8.0 or 8.4 LTS with `utf8mb4`
- Node.js 20+ and npm with the committed dependency versions

## Database

For a new disposable verification database, run `scripts/windows/init_database.ps1 -Mode Fresh`; it defaults to `population_miniterm_integration_verify`, rejects every non-empty database, and rejects the course database unless explicitly confirmed twice. For an upgrade, back up first and execute V4_001 through V4_010 in filename order; Fresh is never an upgrade tool. Run `scripts/windows/init_database.ps1 -Mode Verify` before deployment. `doc/database/demo_data.sql` is optional and only for course demonstrations.

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

Build and start the frontend with `npm ci`, `npm run build`, or `npm run dev` under `frontend`. Prefer `VITE_API_BASE_URL=/api` on the same origin. `npm run dev` provides the Vite proxy; preview and static `dist` deployments must configure an equivalent reverse proxy (see `config/nginx.population.conf.example`).

Upload and export directories must be writable by the application account, outside the web root, excluded from Git, and included in retention/backup policy. Back up MySQL before every migration and regularly test restore procedures. Default course accounts use `123456` only in local demo data and must be disabled or changed in any deployed environment.

Common failures: verify MySQL version and timezone, DB grants, `utf8mb4`, JWT length, directory permissions, reverse-proxy body limits, and that migrations were applied in order. Do not bypass foreign keys to work around invalid data.
