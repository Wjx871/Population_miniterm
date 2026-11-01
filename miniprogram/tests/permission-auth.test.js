const test = require('node:test')
const assert = require('node:assert/strict')
const localStorage = new Map()
global.wx = { getStorageSync: (key) => localStorage.get(key), setStorageSync: (key, value) => localStorage.set(key, value), removeStorageSync: (key) => localStorage.delete(key), showToast() {} }
const permission = require('../utils/permission')
const storageApi = require('../utils/storage')

test('页面入口只按 permissions 判断', () => { const user = { roleCode: 'SYSTEM_ADMIN', permissions: ['population:view'] }; assert.equal(permission.can(user, 'population:view'), true); assert.equal(permission.can(user, 'approval:view'), false) })
test('审批入口与审批操作权限分别判断', () => { const user = { permissions: ['approval:view'] }; assert.equal(permission.can(user, 'approval:view'), true); assert.equal(permission.can(user, 'approval:handle'), false) })
test('记住账号不保存密码', () => { storageApi.rememberUsername('admin'); assert.equal(storageApi.getRememberedUsername(), 'admin'); assert.equal([...localStorage.keys()].some((key) => /password/i.test(key)), false) })
test('logout 清理 Token 和用户但保留记住账号', () => { storageApi.setToken('token'); storageApi.setUser({ userId: 1 }); storageApi.clearSession(); assert.equal(storageApi.getToken(), ''); assert.equal(storageApi.getUser(), null); assert.equal(storageApi.getRememberedUsername(), 'admin') })
