import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'

test('注销、重点人口与敏感导出统一注册专业 Handler', async () => {
  const [{ createCancellationHandler }, { createKeyPopulationHandler }, { createExportHandler }] = await Promise.all([
    import('../src/features/applications/handlers/cancellationHandler.js'),
    import('../src/features/applications/handlers/keyPopulationHandler.js'),
    import('../src/features/applications/handlers/exportHandler.js'),
  ])
  const handlers = [createCancellationHandler({}), createKeyPopulationHandler({}), createExportHandler({})]
  const types = ['PERSON_CANCELLATION', 'HOUSEHOLD_CANCELLATION', 'KEY_POPULATION_REGISTER', 'KEY_POPULATION_RELEASE', 'SENSITIVE_DATA_EXPORT']
  for (const type of types) {
    const handler = handlers.find((candidate) => candidate.supports(type))
    assert.ok(handler)
    assert.equal(typeof handler.execute, 'function')
  }
})

test('专业材料类型与后端提交校验规则一致', async () => {
  const { getKeyPopulationMaterialOptions } = await import('../src/constants/keyPopulation.js')
  const { getSensitiveExportMaterialOptions } = await import('../src/constants/export.js')
  const { getCancellationMaterialOptions } = await import('../src/constants/cancellation.js')

  assert.deepEqual(getKeyPopulationMaterialOptions().map((item) => item.value), [
    'KEY_POPULATION_BASIS',
    'SITUATION_DESCRIPTION',
  ])
  assert.deepEqual(getSensitiveExportMaterialOptions().map((item) => item.value), [
    'EXPORT_JUSTIFICATION',
    'APPLICANT_IDENTITY_PROOF',
    'SITUATION_DESCRIPTION',
  ])
  const death = getCancellationMaterialOptions('PERSON', 'DEATH').map((item) => item.value)
  assert.ok(death.includes('DEATH_CERTIFICATE'))
  assert.ok(death.includes('HOUSEHOLD_BOOK'))
})

test('复杂业务使用正式权限、专业详情和显式执行接口', async () => {
  const [routes, detail, handlerIndex, cancellationApi, keyApi, exportApi] = await Promise.all([
    readFile(new URL('../src/router/routes.js', import.meta.url), 'utf8'),
    readFile(new URL('../src/views/applications/ApplicationDetail.vue', import.meta.url), 'utf8'),
    readFile(new URL('../src/features/applications/handlers/index.js', import.meta.url), 'utf8'),
    readFile(new URL('../src/api/cancellations.js', import.meta.url), 'utf8'),
    readFile(new URL('../src/api/keyPopulation.js', import.meta.url), 'utf8'),
    readFile(new URL('../src/api/exports.js', import.meta.url), 'utf8'),
  ])

  assert.doesNotMatch(routes, /重点人口管理正在建设中/)
  assert.match(detail, /application\.value\?\.status !== 'APPROVED'/)
  assert.match(handlerIndex, /createCancellationHandler/)
  assert.match(handlerIndex, /createKeyPopulationHandler/)
  assert.match(handlerIndex, /createExportHandler/)
  assert.match(cancellationApi, /\/execute`.*method:'post'/)
  assert.match(keyApi, /\/execute`[\s\S]*method:\s*'post'/)
  assert.match(exportApi, /\/execute`.*method:'post'/)
})
