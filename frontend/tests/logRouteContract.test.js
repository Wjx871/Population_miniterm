import test from 'node:test'
import assert from 'node:assert/strict'

import { routes } from '../src/router/routes.js'
import { PERMISSIONS } from '../src/constants/permissions.js'

function findRouteByName(routesList, name) {
  for (const route of routesList) {
    if (route.name === name) return route
    if (route.children) {
      const found = findRouteByName(route.children, name)
      if (found) return found
    }
  }
  return null
}

test('操作/登录日志路由使用 log:view 且无删除语义', () => {
  const ops = findRouteByName(routes, 'OperationLogs')
  assert.ok(ops)
  assert.equal(ops.meta.permission, PERMISSIONS.LOG_VIEW)
  assert.equal(ops.meta.menu, true)
  assert.equal(ops.meta.group, '系统管理')

  const detail = findRouteByName(routes, 'OperationLogDetail')
  assert.ok(detail)
  assert.equal(detail.meta.permission, PERMISSIONS.LOG_VIEW)
  assert.equal(detail.meta.menu, false)
  assert.equal(detail.meta.activeMenu, '/logs/operations')

  const logins = findRouteByName(routes, 'LoginLogs')
  assert.ok(logins)
  assert.equal(logins.meta.permission, PERMISSIONS.LOG_VIEW)
  assert.equal(logins.meta.menu, true)
})

test('log:view 权限常量正确', () => {
  assert.equal(PERMISSIONS.LOG_VIEW, 'log:view')
})
