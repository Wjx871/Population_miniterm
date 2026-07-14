import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { normalizeLoginInfo, normalizeStoredSession, normalizeUserInfo } from '../src/stores/userNormalizer.js'

test('本地恢复只信任 token，不信任旧用户权限快照', () => {
  assert.deepEqual(normalizeStoredSession({ accessToken: 'token', tokenType: 'Bearer', permissions: ['*'], roleCode: 'SYSTEM_ADMIN' }), {
    accessToken: 'token',
    tokenType: 'Bearer',
  })
})

test('登录和 me 用户信息统一规范化', () => {
  const normalized = normalizeLoginInfo({ token: 'jwt', user: {
    userId: 5,
    username: 'admin',
    roleCode: 'SYSTEM_ADMIN',
    roleLevel: 'L3',
    dataScope: 'ALL',
    permissions: ['statistics:view'],
  } })
  assert.equal(normalized.accessToken, 'jwt')
  assert.equal(normalized.dataScope, 'ALL')
  assert.deepEqual(normalized.permissions, ['statistics:view'])
  assert.equal(normalizeUserInfo({ roleCode: 'QUERY_VIEWER' }).roleCode, 'QUERY_VIEWER')
})

test('刷新、logout 和 401 使用正式认证生命周期', async () => {
  const [auth, store, request, router, layout] = await Promise.all([
    readFile(new URL('../src/api/auth.js', import.meta.url), 'utf8'),
    readFile(new URL('../src/stores/user.js', import.meta.url), 'utf8'),
    readFile(new URL('../src/api/request.js', import.meta.url), 'utf8'),
    readFile(new URL('../src/router/index.js', import.meta.url), 'utf8'),
    readFile(new URL('../src/layouts/MainLayout.vue', import.meta.url), 'utf8'),
  ])
  assert.match(auth, /url:\s*['"]\/auth\/me['"]/)
  assert.match(auth, /url:\s*['"]\/auth\/logout['"]/)
  assert.match(store, /restoreSession\(\)/)
  assert.match(store, /getCurrentUser\(\)/)
  assert.match(store, /await logoutApi\(\)/)
  assert.match(request, /handlingUnauthorized/)
  assert.match(request, /userStore\.clearSession\(\)/)
  assert.doesNotMatch(request, /userStore\.logout\(\)/)
  assert.match(router, /await userStore\.restoreSession\(\)/)
  assert.match(layout, /await userStore\.logout\(\)/)
})
