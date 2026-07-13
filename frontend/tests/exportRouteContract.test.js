import test from 'node:test'
import assert from 'node:assert/strict'

import { routes } from '../src/router/routes.js'
import { PERMISSIONS } from '../src/constants/permissions.js'
import { BUSINESS_TYPE } from '../src/constants/application.js'
import { createExportHandler } from '../src/features/applications/handlers/exportHandler.js'

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

test('导出记录/普通导出/敏感导出路由契约', () => {
  const list = findRouteByName(routes, 'ExportRecords')
  assert.ok(list)
  assert.equal(list.meta.permission, PERMISSIONS.DATA_EXPORT_LOG_VIEW)
  assert.equal(list.meta.menu, true)
  assert.equal(list.meta.group, '查询统计')

  const normal = findRouteByName(routes, 'NormalExportCreate')
  assert.ok(normal)
  assert.equal(normal.meta.permission, PERMISSIONS.DATA_EXPORT_NORMAL)
  assert.equal(normal.meta.menu, false)
  assert.equal(normal.meta.activeMenu, '/exports')

  const sensitive = findRouteByName(routes, 'SensitiveExportCreate')
  assert.ok(sensitive)
  assert.equal(sensitive.meta.permission, PERMISSIONS.DATA_EXPORT_SENSITIVE_APPLY)
  assert.equal(sensitive.meta.menu, false)
})

test('导出权限常量为完整后端权限码', () => {
  assert.equal(PERMISSIONS.DATA_EXPORT_NORMAL, 'data:export:normal')
  assert.equal(PERMISSIONS.DATA_EXPORT_SENSITIVE_APPLY, 'data:export:sensitive:apply')
  assert.equal(PERMISSIONS.DATA_EXPORT_SENSITIVE_EXECUTE, 'data:export:sensitive:execute')
  assert.equal(PERMISSIONS.DATA_EXPORT_SENSITIVE_DOWNLOAD, 'data:export:sensitive:download')
  assert.equal(PERMISSIONS.DATA_EXPORT_LOG_VIEW, 'data:export:log:view')
})

test('敏感导出业务类型已注册', () => {
  assert.equal(BUSINESS_TYPE.SENSITIVE_DATA_EXPORT, 'SENSITIVE_DATA_EXPORT')
})

test('exportHandler 支持 SENSITIVE_DATA_EXPORT', () => {
  const handler = createExportHandler({})
  assert.equal(handler.supports('SENSITIVE_DATA_EXPORT'), true)
  assert.equal(handler.supports('PERSON_CANCELLATION'), false)
  assert.equal(handler.family, 'export')
})
