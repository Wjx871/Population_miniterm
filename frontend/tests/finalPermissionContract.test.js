import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

import { routes } from '../src/router/routes.js'
import { PERMISSIONS } from '../src/constants/permissions.js'
import { APPLICATION_STATUS, BUSINESS_TYPE } from '../src/constants/application.js'
import { createMigrationHandler } from '../src/features/applications/handlers/migrationHandler.js'
import { createFloatingHandler } from '../src/features/applications/handlers/floatingHandler.js'
import { createResidencePermitHandler } from '../src/features/applications/handlers/residencePermitHandler.js'
import { createCancellationHandler } from '../src/features/applications/handlers/cancellationHandler.js'
import { createExportHandler } from '../src/features/applications/handlers/exportHandler.js'
import { createKeyPopulationHandler } from '../src/features/applications/handlers/keyPopulationHandler.js'

/**
 * 仅做静态文本扫描，避免在 node --test 下触发 axios/element-plus 等浏览器依赖。
 */
const apiSource = readFileSync(
  new URL('../src/api/applications.js', import.meta.url),
  'utf8'
)

function resolveHandler(handlers, businessType) {
  for (const handler of handlers) {
    if (handler.supports(businessType)) return handler
  }
  return null
}

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

const allHandlers = [
  createMigrationHandler({}),
  createFloatingHandler({}),
  createResidencePermitHandler({}),
  createCancellationHandler({}),
  createExportHandler({}),
  createKeyPopulationHandler({})
]

test('五种新业务类型均有 Handler，未知返回 null', () => {
  assert.ok(resolveHandler(allHandlers, 'PERSON_CANCELLATION'))
  assert.ok(resolveHandler(allHandlers, 'HOUSEHOLD_CANCELLATION'))
  assert.ok(resolveHandler(allHandlers, 'SENSITIVE_DATA_EXPORT'))
  assert.ok(resolveHandler(allHandlers, 'KEY_POPULATION_REGISTER'))
  assert.ok(resolveHandler(allHandlers, 'KEY_POPULATION_RELEASE'))
  assert.equal(resolveHandler(allHandlers, 'UNKNOWN'), null)
})

test('旧三类 Handler 仍可识别', () => {
  assert.ok(resolveHandler(allHandlers, 'MIGRATION_IN'))
  assert.ok(resolveHandler(allHandlers, 'FLOATING_REGISTRATION'))
  assert.ok(resolveHandler(allHandlers, 'RESIDENCE_PERMIT_FIRST_ISSUE'))
})

test('重点人口有专业 submit，注销/导出回退通用 submit', () => {
  const key = createKeyPopulationHandler({})
  const cancel = createCancellationHandler({})
  const exp = createExportHandler({})
  assert.equal(typeof key.submit, 'function')
  assert.equal(typeof cancel.submit, 'undefined')
  assert.equal(typeof exp.submit, 'undefined')
})

test('最终权限码集合完整且无过时 key:*', () => {
  const required = [
    'CANCELLATION_VIEW',
    'CANCELLATION_PERSON_CREATE',
    'CANCELLATION_HOUSEHOLD_CREATE',
    'CANCELLATION_EXECUTE',
    'CANCELLATION_ARCHIVE_VIEW',
    'DATA_EXPORT_NORMAL',
    'DATA_EXPORT_SENSITIVE_APPLY',
    'DATA_EXPORT_SENSITIVE_EXECUTE',
    'DATA_EXPORT_SENSITIVE_DOWNLOAD',
    'DATA_EXPORT_LOG_VIEW',
    'KEY_POPULATION_VIEW',
    'KEY_POPULATION_APPLY',
    'KEY_POPULATION_EXECUTE',
    'LOG_VIEW'
  ]
  for (const key of required) {
    assert.ok(PERMISSIONS[key], `缺少权限常量 ${key}`)
    assert.ok(!String(PERMISSIONS[key]).startsWith('key:'), `${key} 不得使用过时 key:*`)
  }
  assert.equal(PERMISSIONS.KEY_VIEW, undefined)
})

test('最终菜单路由均存在', () => {
  for (const name of [
    'Cancellations',
    'ExportRecords',
    'KeyPopulation',
    'OperationLogs',
    'LoginLogs'
  ]) {
    assert.ok(findRouteByName(routes, name), `路由 ${name} 应存在`)
  }
})

test('业务类型常量覆盖最终阶段五类', () => {
  assert.ok(BUSINESS_TYPE.PERSON_CANCELLATION)
  assert.ok(BUSINESS_TYPE.HOUSEHOLD_CANCELLATION)
  assert.ok(BUSINESS_TYPE.SENSITIVE_DATA_EXPORT)
  assert.ok(BUSINESS_TYPE.KEY_POPULATION_REGISTER)
  assert.ok(BUSINESS_TYPE.KEY_POPULATION_RELEASE)
})

test('application:return 权限码已上线且对应 API 调用已注册', () => {
  assert.equal(PERMISSIONS.APPLICATION_RETURN, 'application:return')
  // api/applications.js 必须暴露 returnApplication 调用，与后端 POST /applications/{id}/return 对齐。
  // 这里用文本扫描来避免引入 axios / element-plus 等浏览器环境依赖。
  assert.ok(/export\s+function\s+returnApplication\s*\(/.test(apiSource),
    'src/api/applications.js 必须导出 returnApplication 函数')
  assert.ok(/\/applications\/\$\{applicationId\}\/return/.test(apiSource),
    'returnApplication 必须访问 /applications/{id}/return 路径')
  // APPLICATION_STATUS 必须包含 RETURNED 状态标签。
  assert.ok(APPLICATION_STATUS.RETURNED)
  assert.ok(APPLICATION_STATUS.DRAFT)
  assert.ok(APPLICATION_STATUS.APPROVED)
})
