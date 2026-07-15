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

function findRouteByPath(routesList, path) {
  for (const route of routesList) {
    if (route.path === path) return route
    if (route.children) {
      const found = findRouteByPath(route.children, path)
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

test('routeContract: launch page is public and app shell keeps authentication', () => {
  const launchRoute = findRouteByName(routes, 'Launch')
  assert.ok(launchRoute, 'Launch route should exist')
  assert.equal(launchRoute.path, '/')
  assert.equal(launchRoute.meta.public, true)
  assert.equal(launchRoute.meta.requiresAuth, undefined)

  const loginRoute = findRouteByName(routes, 'Login')
  assert.equal(loginRoute.path, '/login')
  assert.equal(loginRoute.meta.requiresAuth, undefined)

  const appShell = findRouteByName(routes, 'AppShell')
  assert.ok(appShell, 'Authenticated app shell should exist')
  assert.equal(appShell.path, '/app')
  assert.equal(appShell.redirect, '/home')
  assert.equal(appShell.meta.requiresAuth, true)
})

test('routeContract: existing business URLs remain absolute with permission metadata', () => {
  const expectedRoutes = [
    ['/home', 'statistics:view', 1],
    ['/persons', 'population:view', 1],
    ['/households', 'household:view', 1],
    ['/migrations/in', 'migration:view', 1],
    ['/floating-population', 'floating:view', 1],
    ['/dictionary', 'dictionary:view', 1],
    ['/region', 'region:view', 1],
  ]

  for (const [path, permission, minLevel] of expectedRoutes) {
    const route = findRouteByPath(routes, path)
    assert.ok(route, `${path} route should exist`)
    assert.equal(route.path.startsWith('/'), true)
    assert.equal(route.meta.permission, permission)
    assert.equal(route.meta.minLevel, minLevel)
  }

  const catchAll = routes.find((route) => route.path === '/:pathMatch(.*)*')
  assert.equal(catchAll.redirect, '/')
})
