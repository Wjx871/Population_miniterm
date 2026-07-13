import test from 'node:test'
import assert from 'node:assert/strict'

import { routes } from '../src/router/routes.js'
import { PERMISSIONS } from '../src/constants/permissions.js'
import { BUSINESS_TYPE } from '../src/constants/application.js'
import { createKeyPopulationHandler } from '../src/features/applications/handlers/keyPopulationHandler.js'

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

test('重点人口路由正式开放且使用 key-population:* 权限', () => {
  const list = findRouteByName(routes, 'KeyPopulation')
  assert.ok(list)
  assert.equal(list.meta.permission, PERMISSIONS.KEY_POPULATION_VIEW)
  assert.equal(list.meta.menu, true)
  assert.notEqual(list.meta.permission, 'key:view')

  const register = findRouteByName(routes, 'KeyPopulationRegister')
  assert.ok(register)
  assert.equal(register.meta.permission, PERMISSIONS.KEY_POPULATION_APPLY)
  assert.equal(register.meta.menu, false)
  assert.equal(register.meta.activeMenu, '/key-population')

  const detail = findRouteByName(routes, 'KeyPopulationDetail')
  assert.ok(detail)
  assert.equal(detail.meta.permission, PERMISSIONS.KEY_POPULATION_VIEW)

  const history = findRouteByName(routes, 'KeyPopulationHistory')
  assert.ok(history)
  const release = findRouteByName(routes, 'KeyPopulationRelease')
  assert.ok(release)
  assert.equal(release.meta.permission, PERMISSIONS.KEY_POPULATION_APPLY)
})

test('重点人口权限常量完整', () => {
  assert.equal(PERMISSIONS.KEY_POPULATION_VIEW, 'key-population:view')
  assert.equal(PERMISSIONS.KEY_POPULATION_APPLY, 'key-population:apply')
  assert.equal(PERMISSIONS.KEY_POPULATION_EXECUTE, 'key-population:execute')
  assert.equal(PERMISSIONS.KEY_VIEW, undefined)
  assert.equal(PERMISSIONS.KEY_APPLY, undefined)
  assert.equal(PERMISSIONS.KEY_MANAGE, undefined)
})

test('业务类型含建档与解除', () => {
  assert.equal(BUSINESS_TYPE.KEY_POPULATION_REGISTER, 'KEY_POPULATION_REGISTER')
  assert.equal(BUSINESS_TYPE.KEY_POPULATION_RELEASE, 'KEY_POPULATION_RELEASE')
})

test('handler 支持两类业务', () => {
  const handler = createKeyPopulationHandler({})
  assert.equal(handler.supports('KEY_POPULATION_REGISTER'), true)
  assert.equal(handler.supports('KEY_POPULATION_RELEASE'), true)
  assert.equal(handler.supports('SENSITIVE_DATA_EXPORT'), false)
})
