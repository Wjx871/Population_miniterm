import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { normalizeHouseholdQueryPage, normalizeLogPage, normalizeMigrationQueryPage } from '../src/adapters/query.js'

test('Phase 11 查询适配器保留后端分页和布尔真值', () => {
  const households = normalizeHouseholdQueryPage({ content: [{ householdId: 1, memberCount: 0, containsKeyPopulation: false }], totalElements: 1, number: 0, size: 10 })
  assert.equal(households.total, 1)
  assert.equal(households.records[0].memberCount, 0)
  assert.equal(households.records[0].containsKeyPopulation, false)

  const migrations = normalizeMigrationQueryPage({ content: [{ direction: 'IN', migrationId: 2, executeDate: '2026-07-01' }], totalElements: 1 })
  assert.equal(migrations.records[0].direction, 'IN')
  assert.equal(migrations.records[0].executeDate, '2026-07-01')
})

test('日志适配器只消费后端脱敏字段且不伪造空值', () => {
  const page = normalizeLogPage({ content: [{ logId: 3, detail: null, errorMessage: null }], totalElements: 1 })
  assert.equal(page.records[0].detail, '')
  assert.equal(page.records[0].errorMessage, '')
})

test('查询与日志页面使用正式 Phase 11 路径并区分失败和空结果', async () => {
  const [queryApi, logApi, personView, householdView, migrationView, logView, routes] = await Promise.all([
    readFile(new URL('../src/api/query.js', import.meta.url), 'utf8'),
    readFile(new URL('../src/api/logs.js', import.meta.url), 'utf8'),
    readFile(new URL('../src/views/query/ComprehensiveQuery.vue', import.meta.url), 'utf8'),
    readFile(new URL('../src/views/query/HouseholdQuery.vue', import.meta.url), 'utf8'),
    readFile(new URL('../src/views/query/MigrationHistoryQuery.vue', import.meta.url), 'utf8'),
    readFile(new URL('../src/views/logs/LogQuery.vue', import.meta.url), 'utf8'),
    readFile(new URL('../src/router/routes.js', import.meta.url), 'utf8'),
  ])
  assert.match(queryApi, /\/query\/persons/)
  assert.match(queryApi, /\/query\/households/)
  assert.match(queryApi, /\/query\/migration-history/)
  assert.match(logApi, /\/logs\/operations/)
  assert.match(logApi, /\/logs\/logins/)
  for (const view of [personView, householdView, migrationView, logView]) {
    assert.match(view, /v-if="(?:loadError|error)"/)
    assert.match(view, /重试/)
  }
  assert.match(routes, /permission:\s*PERMISSIONS\.LOG_VIEW/)
})
