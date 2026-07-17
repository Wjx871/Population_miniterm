import test from 'node:test'
import assert from 'node:assert/strict'
import { RESIDENCE_REASON, getPermitMaterialOptions } from '../src/constants/floatingResidence.js'

test('流动人口居住事由枚举与后端接受值一致，且不含 WORK', () => {
  const accepted = [
    'EMPLOYMENT',
    'STUDY',
    'FAMILY',
    'FAMILY_REUNION',
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
  assert.equal(RESIDENCE_REASON.FAMILY_REUNION, '家庭团聚')
  assert.equal(RESIDENCE_REASON.BUSINESS, '经商')
  assert.equal(RESIDENCE_REASON.OTHER_APPROVED, '其他')
})

test('家庭团聚存量登记在首次申领时要求亲属关系证明', () => {
  const options = getPermitMaterialOptions('FIRST_ISSUE', 'FAMILY_REUNION')
  assert.equal(options.find((item) => item.value === 'FAMILY_RELATIONSHIP_PROOF')?.required, true)
  assert.equal(options.some((item) => item.value === 'OTHER_SUPPORTING_DOCUMENT'), false)
})
