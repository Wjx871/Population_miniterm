import test from 'node:test'
import assert from 'node:assert/strict'
import {
  normalizeHousehold,
  toCreateHouseholdPayload,
  toUpdateHouseholdPayload,
} from '../src/adapters/household.js'

test('toCreateHouseholdPayload includes backend required fields', () => {
  const payload = toCreateHouseholdPayload({
    householdNo: ' HH2026001 ',
    headPersonId: '12',
    householdType: 'FAMILY',
    regionCode: '110105',
    address: ' 示例地址 ',
    establishDate: '2026-07-13',
  })

  assert.deepEqual(payload, {
    householdNo: 'HH2026001',
    headPersonId: 12,
    householdType: 'FAMILY',
    regionCode: '110105',
    address: '示例地址',
    establishDate: '2026-07-13',
  })
})

test('toCreateHouseholdPayload defaults householdType and null head', () => {
  const payload = toCreateHouseholdPayload({
    householdNo: 'HH1',
    regionCode: '110000',
    address: 'A',
    establishDate: '2026-01-01',
  })
  assert.equal(payload.householdType, 'FAMILY')
  assert.equal(payload.headPersonId, null)
})

test('toUpdateHouseholdPayload keeps status and version for optimistic lock', () => {
  const payload = toUpdateHouseholdPayload({
    householdType: 'COLLECTIVE',
    regionCode: '110101',
    address: 'B',
    establishDate: '2026-02-02',
    status: 'ACTIVE',
    version: 3,
  })
  assert.deepEqual(payload, {
    householdType: 'COLLECTIVE',
    regionCode: '110101',
    address: 'B',
    establishDate: '2026-02-02',
    status: 'ACTIVE',
    version: 3,
  })
})

test('normalizeHousehold maps regionCode version and activeMemberCount', () => {
  const view = normalizeHousehold({
    householdId: 9,
    householdNo: 'HH9',
    regionCode: '110105001',
    householdType: 'FAMILY',
    activeMemberCount: 4,
    version: 2,
    status: 'ACTIVE',
  })
  assert.equal(view.id, 9)
  assert.equal(view.regionCode, '110105001')
  assert.equal(view.memberCount, 4)
  assert.equal(view.version, 2)
})
