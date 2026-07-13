import test from 'node:test'
import assert from 'node:assert/strict'
import { parseRoleLevel, resolvePermissionLevel, ROLE_CODE, ROLE_LABEL } from '../src/constants/roles.js'

test('roleMapping: ROLE_CODE and ROLE_LABEL exist and map correctly', () => {
  assert.equal(ROLE_CODE.QUERY_VIEWER, 'QUERY_VIEWER')
  assert.equal(ROLE_LABEL[ROLE_CODE.QUERY_VIEWER], '查询统计人员')
  assert.equal(ROLE_CODE.POPULATION_MANAGER, 'POPULATION_MANAGER')
  assert.equal(ROLE_LABEL[ROLE_CODE.POPULATION_MANAGER], '人口信息管理人员')
  assert.equal(ROLE_CODE.HOUSEHOLD_MANAGER, 'HOUSEHOLD_MANAGER')
  assert.equal(ROLE_LABEL[ROLE_CODE.HOUSEHOLD_MANAGER], '户籍管理人员')
  assert.equal(ROLE_CODE.APPROVER, 'APPROVER')
  assert.equal(ROLE_LABEL[ROLE_CODE.APPROVER], '审批人员')
  assert.equal(ROLE_CODE.SYSTEM_ADMIN, 'SYSTEM_ADMIN')
  assert.equal(ROLE_LABEL[ROLE_CODE.SYSTEM_ADMIN], '系统管理员')
})

test('roleMapping: L1/L2/L3 正常解析', () => {
  assert.equal(parseRoleLevel('L1'), 1)
  assert.equal(parseRoleLevel('l2'), 2)
  assert.equal(parseRoleLevel('L3'), 3)
})

test('roleMapping: 未知等级返回 null', () => {
  assert.equal(parseRoleLevel(null), null)
  assert.equal(parseRoleLevel('L4'), null)
  assert.equal(parseRoleLevel(''), null)
  assert.equal(parseRoleLevel('admin'), null)
})

test('roleMapping: 缺少 roleLevel 时按照真实角色映射兜底, 未知等级降级为 1', () => {
  assert.equal(resolvePermissionLevel(null, ROLE_CODE.SYSTEM_ADMIN), 3)
  assert.equal(resolvePermissionLevel('L4', ROLE_CODE.HOUSEHOLD_MANAGER), 2)
  assert.equal(resolvePermissionLevel('', 'UNKNOWN_ROLE'), 1)
})
