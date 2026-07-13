import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'

const read = (path) => readFile(new URL(path, import.meta.url), 'utf8')

test('integration configuration uses the health endpoint and /api proxy contract', async () => {
  const [envExample, viteConfig, startAll, startFrontend, healthScript, authApi, readme, fullStack, databaseCheck] = await Promise.all([
    read('../.env.example'),
    read('../vite.config.js'),
    read('../../scripts/windows/start_all.bat'),
    read('../../scripts/windows/start_frontend.bat'),
    read('../../scripts/windows/check_backend_health.ps1'),
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
  assert.match(healthScript, /\/api\/health/)
  assert.doesNotMatch(startAll, /\/api\/auth\/login/)
  assert.doesNotMatch(startFrontend, /\/api\/auth\/login/)
  assert.doesNotMatch(authApi, /\/auth\/register/)
  assert.match(readme, /V4_010/)
  assert.match(fullStack, /\$frontend\/api\/auth\/login/)
  assert.match(fullStack, /Forbidden GET/)
  assert.match(databaseCheck, /required_table_missing/)
  assert.match(databaseCheck, /required_index_missing/)
})
