import test from 'node:test'
import assert from 'node:assert/strict'
import { normalizeComprehensivePage, normalizeComprehensivePerson, normalizeComprehensiveProfile } from '../src/adapters/comprehensiveQuery.js'

test('综合查询分页保留 0 并规范化脱敏字段', () => {
  const page = normalizeComprehensivePage({ content: [{ personId: 0, maskedIdentityNo: '1101**********0011', householdHead: false }], totalElements: 0, number: 0, size: 10 })
  assert.equal(page.total, 0)
  assert.equal(page.records[0].personId, 0)
  assert.equal(page.records[0].householdHead, false)
  assert.equal(page.records[0].maskedIdentityNo, '1101**********0011')
})

test('综合档案缺失对象保持 null，缺失历史返回空数组', () => {
  const profile = normalizeComprehensiveProfile({ person: { personId: 1 } })
  assert.equal(profile.currentResidence, null)
  assert.deepEqual(profile.migrationHistory, [])
})

test('综合查询适配器保留家庭户主标识的 null 和 false', () => {
  assert.equal(normalizeComprehensivePerson({ householdHead: null }).householdHead, null)
  assert.equal(normalizeComprehensivePerson({ householdHead: false }).householdHead, false)
})
