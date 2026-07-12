import test from 'node:test'
import assert from 'node:assert/strict'
import { resolveLandingPath, canAccessRouteMeta } from '../src/utils/routeAccess.js'

const routes = [
  { path: '/home', meta: { menu: true, order: 1, permission: 'statistics:view', minLevel: 1 } },
  { path: '/queries/comprehensive', meta: { menu: true, order: 35, permission: 'population:view', minLevel: 1 } },
  { path: '/statistics/dashboard', meta: { menu: true, order: 36, permission: 'statistics:view', minLevel: 1 } },
  { path: '/persons', meta: { menu: true, order: 10, permission: 'person:view', minLevel: 1 } }
]

test('仅 population:view 时不落在 /home，优先综合查询', () => {
  const landing = resolveLandingPath(['population:view'], 1, routes)
  assert.equal(landing, '/queries/comprehensive')
})

test('仅 statistics:view 时落在 /home', () => {
  const landing = resolveLandingPath(['statistics:view'], 1, routes)
  assert.equal(landing, '/home')
})

test('无任何菜单权限时回退 /403', () => {
  const landing = resolveLandingPath([], 1, routes)
  assert.equal(landing, '/403')
})

test('canAccessRouteMeta 拒绝无权路由', () => {
  assert.equal(canAccessRouteMeta(['population:view'], 1, routes[0].meta), false)
  assert.equal(canAccessRouteMeta(['population:view'], 1, routes[1].meta), true)
})
