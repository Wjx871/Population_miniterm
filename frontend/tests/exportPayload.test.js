import test from 'node:test'
import assert from 'node:assert/strict'

import {
  toNormalExportPayload,
  toSensitiveExportPayload,
  toExportQueryParams,
  normalizeExportApplication,
  normalizeExportLog
} from '../src/adapters/export.js'
import {
  EXPORT_MODULE_FIELDS,
  SENSITIVE_EXPORT_FIELDS,
  getFieldOptions,
  hasVerifiedSensitiveExportMaterials
} from '../src/constants/export.js'
import { createExportHandler } from '../src/features/applications/handlers/exportHandler.js'

test('普通导出 payload 仅含 module/filters/fields', () => {
  const payload = toNormalExportPayload({
    module: 'PERSON',
    fields: ['name', 'maskedIdentityNo'],
    filters: { name: '张', regionCode: '110000', status: '', junk: 'x' },
    reason: 'should-not-send'
  })
  assert.deepEqual(payload, {
    module: 'PERSON',
    filters: { name: '张', regionCode: '110000' },
    fields: ['name', 'maskedIdentityNo']
  })
  assert.equal('reason' in payload, false)
})

test('敏感导出 payload 包含理由与额度', () => {
  const payload = toSensitiveExportPayload({
    module: 'PERSON',
    fields: ['name', 'identityNo'],
    filters: { regionCode: '110000' },
    reason: '案件核查',
    expectedRowLimit: 500,
    title: '敏感导出申请',
    remark: '备注'
  })
  assert.equal(payload.expectedRowLimit, 500)
  assert.equal(payload.reason, '案件核查')
  assert.equal(payload.title, '敏感导出申请')
  assert.deepEqual(payload.fields, ['name', 'identityNo'])
})

test('普通导出字段白名单不含完整敏感字段', () => {
  const normalFields = getFieldOptions('PERSON', { sensitive: false }).map((f) => f.value)
  for (const sensitive of SENSITIVE_EXPORT_FIELDS) {
    if ((EXPORT_MODULE_FIELDS.PERSON || []).some((f) => f.value === sensitive)) {
      assert.ok(!normalFields.includes(sensitive), `普通导出不应包含 ${sensitive}`)
    }
  }
  assert.ok(normalFields.includes('maskedIdentityNo'))
})

test('导出查询参数仅 exportNo/module/exportType/status', () => {
  const params = toExportQueryParams({
    exportNo: 'E1',
    module: 'PERSON',
    exportType: 'NORMAL_MASKED',
    status: 'COMPLETED',
    current: 1,
    size: 10,
    keyword: 'no'
  })
  assert.equal(params.keyword, undefined)
  assert.equal(params.exportNo, 'E1')
  assert.equal(params.exportType, 'NORMAL_MASKED')
})

test('ExportApplication 规范化读取 requestedFields 与 version', () => {
  const normalized = normalizeExportApplication({
    application: { businessType: 'SENSITIVE_DATA_EXPORT', status: 'APPROVED' },
    professional: {
      exportModule: 'PERSON',
      requestedFields: 'name,identityNo',
      reason: '核查',
      expectedRowLimit: 100,
      businessStatus: 'APPROVED',
      version: 3,
      filterSnapshot: '{"regionCode":"110000"}'
    },
    executable: true,
    unavailableReason: ''
  })
  assert.equal(normalized.professional.module, 'PERSON')
  assert.deepEqual(normalized.professional.fields, ['name', 'identityNo'])
  assert.equal(normalized.professional.version, 3)
  assert.equal(normalized.executable, true)
})

test('ExportLog 规范化 downloadable 与 exportLogId', () => {
  const log = normalizeExportLog({
    exportLogId: 9,
    exportNo: 'EXP-1',
    exportModule: 'PERSON',
    exportType: 'NORMAL_MASKED',
    status: 'COMPLETED',
    downloadable: true,
    downloadCount: 2
  })
  assert.equal(log.exportLogId, 9)
  assert.equal(log.downloadable, true)
})

test('exportHandler execute 仅传 version，无编辑路由', async () => {
  let args = null
  const handler = createExportHandler({
    executeSensitiveExport: async (id, version) => {
      args = { id, version }
      return { exportLogId: 1 }
    }
  })
  assert.equal(handler.supports('SENSITIVE_DATA_EXPORT'), true)
  assert.equal(handler.buildEditRoute(), null)
  assert.equal(typeof handler.submit, 'undefined')

  await handler.execute({
    applicationId: 20,
    detail: { professional: { version: 6 } }
  })
  assert.deepEqual(args, { id: 20, version: 6 })

  const meta = handler.getExecutionMeta({ detail: { professional: { version: 6 } } })
  assert.equal(meta.permission, 'data:export:sensitive:execute')
  assert.equal(meta.mode, 'direct-confirm')
  assert.ok(meta.title)
})

test('无必需材料时敏感导出材料核验通过', () => {
  assert.equal(hasVerifiedSensitiveExportMaterials([]), true)
  assert.equal(
    hasVerifiedSensitiveExportMaterials([
      { materialType: 'EXPORT_JUSTIFICATION', requiredFlag: true, verifyStatus: 'PENDING' }
    ]),
    false
  )
})
