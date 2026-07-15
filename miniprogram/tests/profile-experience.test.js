const test = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const { normalizeProfile } = require('../adapters/profile')

function source(relativePath) {
  return fs.readFileSync(path.resolve(__dirname, relativePath), 'utf8')
}

test('profile maps permission codes to user responsibilities and real routes', () => {
  const profile = normalizeProfile({
    userId: 1,
    permissions: ['population:view', 'application:view', 'approval:view', 'approval:handle']
  })
  assert.deepEqual(profile.features.map((item) => item.key), ['population', 'application', 'approval'])
  assert.ok(profile.duties.some((item) => item.label === '处理权限范围内的审批事项'))
  assert.equal(profile.accountStatusDisplay, '正常')
})

test('profile page no longer requests operational health on show', () => {
  let definition
  let requests = 0
  global.Page = (value) => { definition = value }
  global.getApp = () => ({ globalData: { user: { userId: 1, permissions: [] } } })
  global.wx = { request: () => { requests += 1 }, navigateTo() {} }
  const target = require.resolve('../pages/profile/index')
  delete require.cache[target]
  require(target)
  const page = Object.assign({}, definition, {
    data: Object.assign({}, definition.data),
    setData(update) { Object.assign(this.data, update) },
    getTabBar() { return null }
  })

  page.onShow()
  assert.equal(requests, 0)
  assert.equal(page.loadHealth, undefined)
})

test('profile renders responsibilities without exposing permission codes or infrastructure', () => {
  const markup = source('../pages/profile/index.wxml')
  const script = source('../pages/profile/index.js')
  assert.match(markup, /我的工作职责/)
  assert.match(markup, /可用功能/)
  assert.match(markup, /敏感内容按账号权限隐藏/)
  assert.doesNotMatch(`${markup}\n${script}`, /user\.permissions|数据库|Redis|MySQL|缓存模式|后端健康|loadHealth/)
  assert.match(script, /version: 'V1\.0\.0'/)
})

test('profile logout remains a confirmed session-clearing relaunch', () => {
  const script = source('../pages/profile/index.js')
  assert.match(script, /storage\.clearSession\(\)/)
  assert.match(script, /wx\.reLaunch\(\{ url: '\/pages\/login\/index' \}\)/)
  assert.match(script, /title: '退出登录'/)
})
