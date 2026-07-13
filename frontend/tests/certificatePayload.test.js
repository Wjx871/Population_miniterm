import test from 'node:test'
import assert from 'node:assert/strict'
import {
  containsMaskedValue,
  toCertificateUpdatePayload,
  toCertificateCancelPayload,
} from '../src/adapters/certificate.js'

test('containsMaskedValue should detect masked values', () => {
  assert.equal(containsMaskedValue('1234567890'), false)
  assert.equal(containsMaskedValue('123****890'), true)
  assert.equal(containsMaskedValue('123•890'), true)
  assert.equal(containsMaskedValue('123●890'), true)
  assert.equal(containsMaskedValue(null), false)
})

test('toCertificateUpdatePayload should throw error for masked certificateNo', () => {
  assert.throws(() => {
    toCertificateUpdatePayload({ certificateNo: '123****' }, 1)
  }, new Error('禁止提交脱敏证件号'))
})

test('toCertificateUpdatePayload should generate correct payload', () => {
  const payload = toCertificateUpdatePayload({
    certificateType: 'ID',
    certificateNo: '12345',
    issueDate: '2023-01-01',
  }, 2)
  
  assert.equal(payload.version, 2)
  assert.equal(payload.certificateType, 'ID')
  assert.equal(payload.certificateNo, '12345')
  assert.equal(payload.issueDate, '2023-01-01')
  assert.equal(payload.expireDate, null)
})

test('toCertificateCancelPayload should generate correct payload', () => {
  const payload = toCertificateCancelPayload('reason', 3)
  assert.equal(payload.version, 3)
  assert.equal(payload.reason, 'reason')
})
