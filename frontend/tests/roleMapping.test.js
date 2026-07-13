import test from 'node:test'
import assert from 'node:assert/strict'
import { parseRoleLevel } from '../src/constants/roles.js'

test('parseRoleLevel parses known roles correctly', () => {
  assert.equal(parseRoleLevel('L1'), 1)
  assert.equal(parseRoleLevel('L2'), 2)
  assert.equal(parseRoleLevel('L3'), 3)
})

test('parseRoleLevel fallbacks to 999 for unknown roles', () => {
  assert.equal(parseRoleLevel(null), 999)
  assert.equal(parseRoleLevel(undefined), 999)
  assert.equal(parseRoleLevel('L4'), 999)
  assert.equal(parseRoleLevel(''), 999)
})
