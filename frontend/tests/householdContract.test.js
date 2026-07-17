import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import {
  normalizeHousehold,
  normalizeHouseholdMember,
  toCreateHouseholdPayload,
  toUpdateHouseholdPayload,
} from '../src/adapters/household.js'

test('家庭户创建和更新 payload 与 Backend V1 DTO 一致', () => {
  assert.deepEqual(toCreateHouseholdPayload({
    householdNo: ' H-001 ', headPersonId: 7, address: ' 地址 ', regionCode: '110101',
    householdType: 'FAMILY', establishDate: '2026-01-01',
  }), {
    householdNo: 'H-001', headPersonId: 7, address: '地址', regionCode: '110101',
    householdType: 'FAMILY', establishDate: '2026-01-01',
  })
  assert.deepEqual(toUpdateHouseholdPayload({
    address: '新地址', regionCode: '110101', householdType: 'FAMILY', establishDate: '2026-01-02',
  }, { status: 'ACTIVE', version: 3 }), {
    address: '新地址', regionCode: '110101', householdType: 'FAMILY', establishDate: '2026-01-02',
    status: 'ACTIVE', version: 3,
  })
  assert.throws(() => toUpdateHouseholdPayload({}, { status: 'ACTIVE' }), /版本/)
})

test('家庭户和成员适配保留并发版本及正式成员数', () => {
  assert.equal(normalizeHousehold({ activeMemberCount: 4, version: 2 }).memberCount, 4)
  assert.equal(normalizeHouseholdMember({ memberId: 1, version: 5 }).version, 5)
})

test('成员离户与户主变更使用正式 POST 接口和 household:edit', async () => {
  const [api, detail, list] = await Promise.all([
    readFile(new URL('../src/api/households.js', import.meta.url), 'utf8'),
    readFile(new URL('../src/views/households/HouseholdDetail.vue', import.meta.url), 'utf8'),
    readFile(new URL('../src/views/households/HouseholdList.vue', import.meta.url), 'utf8'),
  ])
  assert.match(api, /members\/\$\{memberId\}\/leave`[^]*method:\s*'post'/)
  assert.match(api, /change-head`[^]*method:\s*'post'/)
  assert.match(detail, /newHeadPersonId/)
  assert.doesNotMatch(detail, /handleRemove|leaveHouseholdMember|移出家庭成员/)
  assert.doesNotMatch(detail + list, /household:(create|update|member:manage|delete)/)
})
