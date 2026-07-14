const test = require('node:test')
const assert = require('node:assert/strict')

const storage = new Map()
const calls = { relaunch: 0, toast: [], requests: [] }
global.wx = {
  getStorageSync: (key) => storage.get(key), setStorageSync: (key, value) => storage.set(key, value), removeStorageSync: (key) => storage.delete(key),
  reLaunch(options) { calls.relaunch += 1; if (options.complete) options.complete() },
  showToast(options) { calls.toast.push(options.title) },
  request(options) { calls.requests.push(options); options.success({ statusCode: 200, data: { code: 200, message: 'ok', data: { ok: true } } }) }
}
global.getApp = () => ({ setUser() {} })

const storageApi = require('../utils/storage')
const requestApi = require('../services/request')

test('统一解包 ApiResponse.data', () => assert.deepEqual(requestApi.parseResponse({ statusCode: 200, data: { code: 200, data: { id: 1 } } }), { id: 1 }))
test('Token 自动加入 Bearer 请求头', () => { storageApi.setToken('test-token'); assert.equal(requestApi.headers().Authorization, 'Bearer test-token') })
test('并发 401 清理会话且只跳转一次登录页', () => { const before = calls.relaunch; requestApi.reset401Guard(); storageApi.setToken('expired'); storageApi.setUser({ userId: 1 }); assert.throws(() => requestApi.parseResponse({ statusCode: 401, data: { code: 401 } }), /登录状态已失效/); assert.throws(() => requestApi.parseResponse({ statusCode: 401, data: { code: 401 } }), /登录状态已失效/); assert.equal(storageApi.getToken(), ''); assert.equal(storageApi.getUser(), null); assert.equal(calls.relaunch - before, 1) })
test('403 提示权限不足且不清理 Token', () => { storageApi.setToken('valid'); assert.throws(() => requestApi.parseResponse({ statusCode: 403, data: { code: 403 } }), /权限不足/); assert.equal(storageApi.getToken(), 'valid'); assert.equal(calls.toast.at(-1), '权限不足') })
test('400 展示后端具体消息', () => assert.throws(() => requestApi.parseResponse({ statusCode: 400, data: { code: 400, message: '用户名不能为空' } }), /用户名不能为空/))
test('404 使用记录不可见文案', () => assert.throws(() => requestApi.parseResponse({ statusCode: 404, data: { code: 404 } }), /记录不存在或无权查看/))
test('409 展示业务冲突消息', () => assert.throws(() => requestApi.parseResponse({ statusCode: 409, data: { code: 409, message: '审批已被处理' } }), /审批已被处理/))
test('500 不伪装成功或空数据', () => assert.throws(() => requestApi.parseResponse({ statusCode: 500, data: { code: 500 } }), /系统服务异常/))
test('服务请求使用配置后的完整后端路径', async () => { const value = await requestApi.request({ url: '/api/auth/me' }); assert.deepEqual(value, { ok: true }); assert.match(calls.requests.at(-1).url, /http:\/\/127\.0\.0\.1:8080\/api\/auth\/me$/) })
