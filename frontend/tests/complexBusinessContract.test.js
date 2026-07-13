import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { createDirectBusinessHandler } from '../src/features/applications/handlers/directBusinessHandler.js'

function services() {
  const calls = []
  const service = { calls }
  for (const name of ['getCancellationApplication','getKeyRegisterApplication','getKeyReleaseApplication','getExportApplication','executePersonCancellation','executeHouseholdCancellation','executeKeyRegister','executeKeyRelease','executeSensitiveExport']) {
    service[name] = async (...args) => { calls.push([name, ...args]); return { name } }
  }
  return service
}

test('注销、重点人口与敏感导出统一使用专业详情和显式执行接口', async () => {
  const service = services()
  const handler = createDirectBusinessHandler(service)
  for (const type of ['PERSON_CANCELLATION','HOUSEHOLD_CANCELLATION','KEY_POPULATION_REGISTER','KEY_POPULATION_RELEASE','SENSITIVE_DATA_EXPORT']) {
    assert.equal(handler.supports(type), true)
    await handler.loadDetail(8, type)
  }
  await handler.execute({ businessType: 'PERSON_CANCELLATION', applicationId: 8, detail: { cancellation: { version: 2 } } })
  await handler.execute({ businessType: 'KEY_POPULATION_REGISTER', applicationId: 9, detail: { application: { version: 3 } } })
  await handler.execute({ businessType: 'SENSITIVE_DATA_EXPORT', applicationId: 10, detail: { professional: { version: 4 } } })
  assert.deepEqual(service.calls.slice(-3), [
    ['executePersonCancellation', 8, 2],
    ['executeKeyRegister', 9, 3],
    ['executeSensitiveExport', 10, 4],
  ])
})

test('专业材料类型与后端提交校验规则一致', () => {
  const handler = createDirectBusinessHandler(services())
  assert.deepEqual(handler.getMaterialOptions({ businessType: 'KEY_POPULATION_REGISTER' }).map(x => x.value), ['KEY_POPULATION_BASIS','SITUATION_DESCRIPTION'])
  assert.deepEqual(handler.getMaterialOptions({ businessType: 'SENSITIVE_DATA_EXPORT' }).map(x => x.value), ['EXPORT_JUSTIFICATION'])
  const death = handler.getMaterialOptions({ businessType: 'PERSON_CANCELLATION', detail: { cancellation: { cancelReasonCode: 'DEATH' } } }).map(x => x.value)
  assert.ok(death.includes('DEATH_CERTIFICATE'))
  assert.ok(death.includes('HOUSEHOLD_BOOK'))
})

test('复杂业务页面不再使用建设中占位且 approver 执行按钮由权限控制', async () => {
  const [routes, detail, handler, cancellationApi, keyApi, exportApi] = await Promise.all([
    readFile(new URL('../src/router/routes.js', import.meta.url), 'utf8'),
    readFile(new URL('../src/views/applications/ApplicationDetail.vue', import.meta.url), 'utf8'),
    readFile(new URL('../src/features/applications/handlers/directBusinessHandler.js', import.meta.url), 'utf8'),
    readFile(new URL('../src/api/cancellations.js', import.meta.url), 'utf8'),
    readFile(new URL('../src/api/keyPopulations.js', import.meta.url), 'utf8'),
    readFile(new URL('../src/api/exports.js', import.meta.url), 'utf8'),
  ])
  assert.doesNotMatch(routes, /重点人口管理正在建设中/)
  assert.match(detail, /application\.value\?\.status !== 'APPROVED'/)
  assert.match(handler, /cancellation:execute/)
  assert.match(handler, /key-population:execute/)
  assert.match(handler, /data:export:sensitive:execute/)
  assert.match(cancellationApi, /\/execute`.*method:'post'/)
  assert.match(keyApi, /\/execute`.*method:'post'/)
  assert.match(exportApi, /\/execute`.*method:'post'/)
})
