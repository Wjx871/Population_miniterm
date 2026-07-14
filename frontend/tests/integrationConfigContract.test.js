import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'

const read = (path) => readFile(new URL(path, import.meta.url), 'utf8')

test('integration configuration uses the health endpoint and /api proxy contract', async () => {
  const [envExample, viteConfig, startAll, startFrontend, healthScript, initDatabase, authApi, readme, fullStack, databaseCheck] = await Promise.all([
    read('../.env.example'),
    read('../vite.config.js'),
    read('../../scripts/windows/start_all.bat'),
    read('../../scripts/windows/start_frontend.bat'),
    read('../../scripts/windows/check_backend_health.ps1'),
    read('../../scripts/windows/init_database.ps1'),
    read('../src/api/auth.js'),
    read('../../README.md'),
    read('../../scripts/windows/verify_full_stack.ps1'),
    read('../../doc/database/check_backend_v1.sql'),
  ])

  assert.match(envExample, /^VITE_API_BASE_URL=\/api$/m)
  assert.match(viteConfig, /127\.0\.0\.1:8080/)
  assert.match(startAll, /check_backend_health\.ps1/)
  assert.match(startFrontend, /check_backend_health\.ps1/)
  assert.match(startFrontend, /API 基址/)
  assert.match(startFrontend, /VITE_API_BASE_URL/)
  assert.match(startFrontend, /npm --prefix "%FRONTEND_DIR%" run dev/)
  assert.match(healthScript, /\/api\/health/)
  assert.match(healthScript, /exit 2/)
  assert.match(healthScript, /exit 3/)
  assert.match(healthScript, /exit 4/)
  assert.match(healthScript, /exit 5/)
  assert.doesNotMatch(startAll, /\/api\/auth\/login/)
  assert.doesNotMatch(startFrontend, /\/api\/auth\/login/)
  assert.doesNotMatch(authApi, /\/auth\/register/)
  assert.match(readme, /V4_010/)
  assert.match(fullStack, /\$frontend\/api\/auth\/login/)
  assert.match(fullStack, /function Assert-HttpSuccess/)
  assert.match(fullStack, /Assert-HttpSuccess \(Invoke-Http -Method Get -Url "\$frontend\/"\)/)
  assert.match(fullStack, /health\.Json\.data\.database/)
  assert.match(fullStack, /population:view/)
  assert.doesNotMatch(fullStack, /person:view/)
  assert.match(fullStack, /foreach \(\$candidate in \$safeReadCandidates\)/)
  assert.match(fullStack, /Forbidden GET/)
  assert.match(initDatabase, /DB_URL/)
  assert.match(initDatabase, /DB_USERNAME/)
  assert.match(initDatabase, /New-RewrittenSqlCopy/)
  assert.match(initDatabase, /New-RewrittenSqlCopy -SourcePath \$demoSql/)
  assert.doesNotMatch(initDatabase, /Invoke-MySqlFile -Path \$demoSql\b/)
  assert.match(initDatabase, /demo_\" \+ \[IO\.Path\]::GetFileName/)
  assert.match(initDatabase, /\[System\.IO\.File\]::WriteAllText/)
  assert.match(databaseCheck, /required_table_missing/)
  assert.match(databaseCheck, /required_index_missing/)
})
