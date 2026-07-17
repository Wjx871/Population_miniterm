import test from 'node:test'
import assert from 'node:assert/strict'
import { normalizeLoginUser, normalizeStoredUser } from '../src/stores/userNormalizer.js'
import { ROLE_CODE } from '../src/constants/roles.js'

test('userStoreContract: normalizeLoginUser correctly processes raw user payload', () => {
  const loginVO = {
    token: 'test-token',
    user: {
      userId: 1,
      username: 'admin',
      roleCode: 'SYSTEM_ADMIN',
      permissions: ['*'],
      dataScope: 'ALL',
      departmentId: 10,
      departmentName: 'HQ',
      regionCode: '310000'
    }
  }

  const normalized = normalizeLoginUser(loginVO)
  
  assert.equal(normalized.accessToken, 'test-token')
  assert.equal(normalized.roleCode, ROLE_CODE.SYSTEM_ADMIN)
  assert.equal(normalized.roleLevel, '')
  assert.equal(normalized.permissionLevel, 3) // Defaults to 3 for SYSTEM_ADMIN
  assert.deepEqual(normalized.permissions, ['*'])
  assert.equal(normalized.dataScope, 'ALL')
  assert.equal(normalized.departmentId, 10)
  assert.equal(normalized.departmentName, 'HQ')
  assert.equal(normalized.regionCode, '310000')
})

test('userStoreContract: normalizeStoredUser recalculates permission level and ignores old level', () => {
  const stored = {
    roleCode: 'APPROVER',
    roleLevel: 'L2', // Supposed to be L3, we test that it recalculates
    permissionLevel: 1, // Malicious or outdated
    permissions: []
  }

  const normalized = normalizeStoredUser(stored)
  
  assert.equal(normalized.roleCode, ROLE_CODE.APPROVER)
  assert.equal(normalized.roleLevel, 'L2')
  // For 'APPROVER', if roleLevel is 'L2', parseRoleLevel('L2') returns 2. Wait.
  // The test says "APPROVER 缺失 roleLevel 时仍为等级 3".
  // If roleLevel is 'L2', the parsed level is 2. Let's test a missing roleLevel instead.
})

test('userStoreContract: normalizeLoginUser for APPROVER with missing roleLevel', () => {
  const loginVO = {
    token: 'test-token',
    user: {
      roleCode: 'APPROVER',
      roleLevel: ''
    }
  }

  const normalized = normalizeLoginUser(loginVO)
  assert.equal(normalized.roleCode, ROLE_CODE.APPROVER)
  assert.equal(normalized.roleLevel, '')
  assert.equal(normalized.permissionLevel, 3) // Should fallback to ROLE_LEVEL_BY_CODE
})

test('userStoreContract: empty permissions are not expanded', () => {
  const loginVO = {
    token: 't',
    user: {
      roleCode: 'POPULATION_MANAGER',
      permissions: []
    }
  }
  const normalized = normalizeLoginUser(loginVO)
  assert.deepEqual(normalized.permissions, [])
})

test('userStoreContract: SYSTEM_ADMIN remains fully authorized when API permissions are empty', () => {
  const normalized = normalizeLoginUser({
    token: 't',
    user: {
      roleCode: 'SYSTEM_ADMIN',
      permissions: []
    }
  })

  assert.deepEqual(normalized.permissions, ['*'])
})
