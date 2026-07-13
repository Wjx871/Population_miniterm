import test from 'node:test'
import assert from 'node:assert/strict'
import { routes } from '../src/router/routes.js'

function findRouteByName(routesList, name) {
  for (const route of routesList) {
    if (route.name === name || route.path.endsWith(name)) return route
    if (route.children) {
      const found = findRouteByName(route.children, name)
      if (found) return found
    }
  }
  return null
}

test('routeContract: verify permission and minLevel requirements', () => {
  // Verify dictionary route
  const dictRoute = findRouteByName(routes, 'dictionary')
  assert.ok(dictRoute, 'Dictionary route should exist')
  assert.equal(dictRoute.meta.permission, 'dictionary:view')
  assert.equal(dictRoute.meta.minLevel, 1)

  // Verify region route
  const regionRoute = findRouteByName(routes, 'region')
  assert.ok(regionRoute, 'Region route should exist')
  assert.equal(regionRoute.meta.permission, 'region:view')
  assert.equal(regionRoute.meta.minLevel, 1)
})
