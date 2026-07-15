const test = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')

let pageDefinition
const auth = require('../services/auth')
const storage = require('../utils/storage')

global.Page = (definition) => { pageDefinition = definition }
global.getApp = () => ({ setUser(user) { calls.appUser = user } })

const calls = { login: 0, tokens: [], users: [], remembered: [], relaunch: [], appUser: null }
global.wx = {
  getStorageSync() { return '' },
  setStorageSync() {},
  removeStorageSync() {},
  reLaunch(options) { calls.relaunch.push(options.url) },
  showToast() {}
}

require('../pages/login/index')

function createPage(values = {}) {
  return Object.assign({}, pageDefinition, {
    data: Object.assign({}, pageDefinition.data, values),
    setData(update) { Object.assign(this.data, update) }
  })
}

function resetCalls() {
  calls.login = 0
  calls.tokens = []
  calls.users = []
  calls.remembered = []
  calls.relaunch = []
  calls.appUser = null
  auth.login = async () => { calls.login += 1; return { token: 'session-token' } }
  auth.me = async () => ({ userId: 1, username: 'operator', realName: '经办人员', permissions: [] })
  storage.setToken = (token) => calls.tokens.push(token)
  storage.setUser = (user) => calls.users.push(user)
  storage.rememberUsername = (username) => calls.remembered.push(username)
  storage.clearSession = () => {}
}

test('login page does not request login with an empty username', async () => {
  resetCalls()
  const page = createPage({ username: '  ', password: 'secret' })
  await page.submit()
  assert.equal(calls.login, 0)
  assert.equal(page.data.error, '请输入用户名')
})

test('login page does not request login with an empty password', async () => {
  resetCalls()
  const page = createPage({ username: 'operator', password: '' })
  await page.submit()
  assert.equal(calls.login, 0)
  assert.equal(page.data.error, '请输入密码')
})

test('login page toggles password visibility', () => {
  const page = createPage()
  page.togglePassword()
  assert.equal(page.data.showPassword, true)
  page.togglePassword()
  assert.equal(page.data.showPassword, false)
})

test('login page loading guard prevents duplicate requests', async () => {
  resetCalls()
  let release
  auth.login = () => { calls.login += 1; return new Promise((resolve) => { release = resolve }) }
  const page = createPage({ username: 'operator', password: 'secret' })
  const first = page.submit()
  const second = page.submit()
  assert.equal(calls.login, 1)
  release({ token: 'session-token' })
  await Promise.all([first, second])
})

test('login page shows a safe error and clears the password after failure', async () => {
  resetCalls()
  auth.login = async () => { calls.login += 1; throw Object.assign(new Error('java.sql.SQLException'), { statusCode: 500 }) }
  const page = createPage({ username: 'operator', password: 'secret' })
  await page.submit()
  assert.equal(page.data.error, '暂时无法登录，请稍后重试')
  assert.equal(page.data.password, '')
})

test('login page saves the session and navigates after success', async () => {
  resetCalls()
  const page = createPage({ username: ' operator ', password: 'secret', remember: true })
  await page.submit()
  assert.deepEqual(calls.tokens, ['session-token'])
  assert.equal(calls.users[0].userId, 1)
  assert.deepEqual(calls.remembered, ['operator'])
  assert.equal(calls.appUser.userId, 1)
  assert.deepEqual(calls.relaunch, ['/pages/dashboard/index'])
  assert.equal(page.data.password, '')
})

test('login page source has no unsupported account entry or default credential', () => {
  const wxml = fs.readFileSync(path.resolve(__dirname, '../pages/login/index.wxml'), 'utf8')
  const javascript = fs.readFileSync(path.resolve(__dirname, '../pages/login/index.js'), 'utf8')
  assert.doesNotMatch(wxml, /验证码|注册|忘记密码|微信授权|指纹|人脸/)
  assert.doesNotMatch(`${wxml}\n${javascript}`, /123456|defaultPassword|test-password/)
})
