import test from 'node:test'
import assert from 'node:assert/strict'
import { RESIDENCE_REASON } from '../src/constants/floatingResidence.js'

test('流动人口居住事由枚举与后端接受值一致，且不含 WORK', () => {
  const accepted = [
    'EMPLOYMENT',
    'STUDY',
    'FAMILY',
    'BUSINESS',
    'OTHER_APPROVED',
  ]

  assert.deepEqual(Object.keys(RESIDENCE_REASON), accepted)
  assert.equal(Object.hasOwn(RESIDENCE_REASON, 'WORK'), false)
})

test('流动人口居住事由中文标签完整', () => {
  assert.equal(RESIDENCE_REASON.EMPLOYMENT, '就业')
  assert.equal(RESIDENCE_REASON.STUDY, '学习')
  assert.equal(RESIDENCE_REASON.FAMILY, '家庭团聚')
  assert.equal(RESIDENCE_REASON.BUSINESS, '经商')
  assert.equal(RESIDENCE_REASON.OTHER_APPROVED, '其他')
})
