import test from 'node:test'
import assert from 'node:assert/strict'

import { routes } from '../src/router/routes.js'
import { PERMISSIONS } from '../src/constants/permissions.js'
import { BUSINESS_TYPE, BUSINESS_TYPE_LABEL } from '../src/constants/application.js'
import { createCancellationHandler } from '../src/features/applications/handlers/cancellationHandler.js'

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

test('注销列表/申请/归档路由权限与菜单契约', () => {
  const list = findRouteByName(routes, 'Cancellations')
  assert.ok(list, '注销列表路由应存在')
  assert.equal(list.meta.permission, PERMISSIONS.CANCELLATION_VIEW)
  assert.equal(list.meta.menu, true)
  assert.equal(list.meta.group, '业务办理')
  assert.equal(list.path, '/cancellations')

  const apply = findRouteByName(routes, 'CancellationApply')
  assert.ok(apply)
  assert.equal(apply.meta.menu, false)
  assert.equal(apply.meta.activeMenu, '/cancellations')

  const archives = findRouteByName(routes, 'HouseholdArchiveList')
  assert.ok(archives)
  assert.equal(archives.meta.permission, PERMISSIONS.CANCELLATION_ARCHIVE_VIEW)
  assert.equal(archives.meta.menu, false)
  assert.equal(archives.meta.activeMenu, '/cancellations')
})

test('注销权限常量为完整后端权限码', () => {
  assert.equal(PERMISSIONS.CANCELLATION_VIEW, 'cancellation:view')
  assert.equal(PERMISSIONS.CANCELLATION_PERSON_CREATE, 'cancellation:person:create')
  assert.equal(PERMISSIONS.CANCELLATION_HOUSEHOLD_CREATE, 'cancellation:household:create')
  assert.equal(PERMISSIONS.CANCELLATION_EXECUTE, 'cancellation:execute')
  assert.equal(PERMISSIONS.CANCELLATION_ARCHIVE_VIEW, 'cancellation:archive:view')
  assert.equal(PERMISSIONS.KEY_VIEW, undefined)
  assert.equal(PERMISSIONS.KEY_POPULATION_VIEW, 'key-population:view')
})

test('业务类型已注册人员/家庭户注销', () => {
  assert.equal(BUSINESS_TYPE.PERSON_CANCELLATION, 'PERSON_CANCELLATION')
  assert.equal(BUSINESS_TYPE.HOUSEHOLD_CANCELLATION, 'HOUSEHOLD_CANCELLATION')
  assert.equal(BUSINESS_TYPE_LABEL.PERSON_CANCELLATION, '人员注销')
  assert.equal(BUSINESS_TYPE_LABEL.HOUSEHOLD_CANCELLATION, '家庭户销户')
})

test('cancellationHandler 支持两类业务并提供编辑路由', () => {
  const handler = createCancellationHandler({})
  assert.equal(handler.supports('PERSON_CANCELLATION'), true)
  assert.equal(handler.supports('HOUSEHOLD_CANCELLATION'), true)
  assert.equal(handler.supports('MIGRATION_IN'), false)

  const personRoute = handler.buildEditRoute({
    applicationId: 5,
    detail: { cancellation: { cancelObjectType: 'PERSON' } }
  })
  assert.equal(personRoute.path, '/cancellations/apply')
  assert.equal(personRoute.query.objectType, 'PERSON')
  assert.equal(personRoute.query.applicationId, 5)

  const householdRoute = handler.buildEditRoute({
    applicationId: 6,
    detail: { cancellation: { cancelObjectType: 'HOUSEHOLD' } }
  })
  assert.equal(householdRoute.query.objectType, 'HOUSEHOLD')
})

test('重点人口占位路由已使用正式权限码', () => {
  const keyRoute = findRouteByName(routes, 'KeyPopulation')
  assert.ok(keyRoute)
  assert.equal(keyRoute.meta.permission, PERMISSIONS.KEY_POPULATION_VIEW)
  assert.notEqual(keyRoute.meta.permission, 'key:view')
})
