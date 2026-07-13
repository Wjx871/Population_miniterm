import test from 'node:test'
import assert from 'node:assert/strict'
import { resolvePermissions, checkAnyPermission } from '../src/utils/permission.js'
import { PERMISSIONS } from '../src/constants/permissions.js'

test('permissionContract: should treat empty permissions array as no permissions, no default fallback', () => {
  const perms = resolvePermissions('admin', []) 
  assert.equal(perms.length, 0)
  assert.equal(checkAnyPermission(perms, ['population:view']), false)
})

test('permissionContract: undefined permissions fallback to empty array', () => {
  const perms = resolvePermissions('admin', undefined)
  assert.equal(perms.length, 0)
})

test('permissionContract: only "*" gives wildcard permission', () => {
  const wildcardPerms = resolvePermissions('admin', ['*'])
  assert.equal(checkAnyPermission(wildcardPerms, ['anything:doesnt:matter']), true)
  
  const normalPerms = resolvePermissions('admin', ['some:permission'])
  assert.equal(checkAnyPermission(normalPerms, ['anything:doesnt:matter']), false)
})

test('permissionContract: verify sensitive data view constant and old constant replaced', () => {
  assert.equal(PERMISSIONS.SENSITIVE_DATA_VIEW_FULL, 'sensitive-data:view-full')
  assert.equal(PERMISSIONS.PERSON_SENSITIVE_VIEW, undefined)
})
