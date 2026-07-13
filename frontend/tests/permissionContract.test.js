import test from 'node:test'
import assert from 'node:assert/strict'
import { resolvePermissions, checkAnyPermission } from '../src/utils/permission.js'

test('permissionContract: should treat empty permissions array as no permissions, no fallback', () => {
  const perms = resolvePermissions('admin', []) 
  assert.equal(perms.length, 0)
  assert.equal(checkAnyPermission(perms, ['population:view']), false)
})

test('permissionContract: should identify valid permissions from array', () => {
  const perms = resolvePermissions('admin', ['population:view'])
  assert.equal(perms.length, 1)
  assert.equal(checkAnyPermission(perms, ['population:view']), true)
  assert.equal(checkAnyPermission(perms, ['population:edit']), false)
})

test('permissionContract: undefined permissions should result in empty array', () => {
  const perms = resolvePermissions('admin', undefined)
  assert.equal(perms.length, 0)
})
