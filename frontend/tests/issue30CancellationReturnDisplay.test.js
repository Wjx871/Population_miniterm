import test from 'node:test'
import assert from 'node:assert/strict'
import { normalizeCancellationRecord, normalizeCancellationProfessional, getCancellationDetailFields } from '../src/adapters/cancellation.js'

test('returned application remains visible beside the prior cancellation status', () => {
  const raw = { applicationId: 1, businessStatus: 'APPROVED', applicationStatus: 'RETURNED' }
  const row = normalizeCancellationRecord(raw)
  assert.equal(row.businessStatus, 'APPROVED')
  assert.equal(row.applicationStatus, 'RETURNED')
  const detail = normalizeCancellationProfessional({ application: { status: 'RETURNED' }, cancellation: raw, executable: false })
  const fields = getCancellationDetailFields(detail)
  assert.equal(fields.find((field) => field.label === '申请状态').value, '已退回，待处理')
  assert.equal(fields.find((field) => field.label === '注销记录状态').value, '已批准')
  assert.equal(detail.executable, false)
})