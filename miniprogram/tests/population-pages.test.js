const test = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')

const personAdapter = require('../adapters/person')
const personService = require('../services/person')

function source(relativePath) {
  return fs.readFileSync(path.resolve(__dirname, relativePath), 'utf8')
}

function loadPage(relativePath) {
  let definition
  global.Page = (value) => { definition = value }
  const target = require.resolve(relativePath)
  delete require.cache[target]
  require(target)
  return definition
}

function createPage(definition, values = {}) {
  return Object.assign({}, definition, {
    data: Object.assign({}, definition.data, values),
    setData(update) { Object.assign(this.data, update) }
  })
}

global.getApp = () => ({ globalData: { user: { permissions: ['population:view'] } } })
global.wx = {
  stopPullDownRefresh() {},
  showToast() {},
  navigateTo() {},
  getStorageSync() { return '' }
}

test('population adapter localizes display fields and preserves masked values', () => {
  const row = personAdapter.normalize({
    personId: 1,
    name: '张三',
    gender: 'M',
    personType: 'RESIDENT',
    idCard: '110***********1234',
    phone: null,
    status: 'NORMAL'
  })
  assert.equal(row.name, '张三')
  assert.equal(row.genderDisplay, '男')
  assert.equal(row.personTypeDisplay, '常住人口')
  assert.equal(row.idCardDisplay, '110***********1234')
  assert.equal(row.phoneDisplay, '未登记')
  assert.equal(row.statusDisplay, '正常')
})

test('population adapter safely handles unknown enums and missing fields', () => {
  const row = personAdapter.normalize({ gender: 'UNRECOGNIZED', status: 'NEW_UNKNOWN_STATUS' })
  assert.equal(row.genderDisplay, '未登记')
  assert.equal(row.statusDisplay, '状态未登记')
  assert.equal(row.birthDateDisplay, '未登记')
  assert.equal(row.name, '未登记')
})

test('population list initial lifecycle sends one cleaned request', async () => {
  const original = personService.list
  const calls = []
  personService.list = async (params) => {
    calls.push(params)
    return { content: [], totalElements: 0, number: 0, last: true }
  }
  const page = createPage(loadPage('../pages/persons/list/index'))
  await Promise.all([page.onLoad(), page.onLoad()])
  assert.equal(calls.length, 1)
  assert.deepEqual(calls[0], { page: 0, size: 10 })
  personService.list = original
})

test('population search and reset both restart pagination', async () => {
  const original = personService.list
  const calls = []
  personService.list = async (params) => {
    calls.push(params)
    return { content: [], totalElements: 0, number: 0, last: true }
  }
  const page = createPage(loadPage('../pages/persons/list/index'), { page: 4, name: ' 张三 ', idCard: ' ', status: 'NORMAL' })
  page._authorized = true
  await page.search()
  page.setData({ page: 6, name: '李四', idCard: '123', status: 'CANCELLED', statusIndex: 3 })
  await page.reset()
  assert.equal(calls[0].page, 0)
  assert.equal(calls[0].name, '张三')
  assert.equal(Object.hasOwn(calls[0], 'idCard'), false)
  assert.deepEqual(calls[1], { page: 0, size: 10 })
  assert.equal(page.data.statusIndex, 0)
  personService.list = original
})

test('population list does not request after the last page', async () => {
  const original = personService.list
  let calls = 0
  personService.list = async () => { calls += 1; return {} }
  const page = createPage(loadPage('../pages/persons/list/index'), { last: true })
  await page.onReachBottom()
  assert.equal(calls, 0)
  personService.list = original
})

test('population list failure remains an error instead of an empty result', async () => {
  const original = personService.list
  personService.list = async () => { throw new Error('unavailable') }
  const page = createPage(loadPage('../pages/persons/list/index'))
  await page.onLoad()
  assert.ok(page.data.error)
  assert.equal(page.data.totalAvailable, false)
  assert.equal(page.data.totalDisplay, '数量暂不可用')
  personService.list = original
})

test('population list uses semantic chevrons and responsive card markup', () => {
  const markup = source('../pages/persons/list/index.wxml')
  assert.match(markup, /name="chevron-right"/)
  assert.match(markup, /<status-tag/)
  assert.doesNotMatch(markup, /[›→]|Emoji|\|\| 0/u)
})

test('population detail has separate summary, identity, residence and household sections', () => {
  const markup = source('../pages/persons/detail/index.wxml')
  for (const title of ['基础信息', '身份与联系方式', '居住信息', '当前家庭关系']) assert.match(markup, new RegExp(title))
  assert.match(markup, /<sensitive-text[^>]*idCardDisplay/)
  assert.match(markup, /<empty-state[\s\S]*暂无有效家庭关系/)
  assert.match(markup, /<error-state[\s\S]*profileError/)
})

test('population detail distinguishes no household from profile failure', async () => {
  const originalDetail = personService.detail
  const originalProfile = personService.profile
  personService.detail = async () => ({ personId: 1, name: '张三', idCard: '110***********1234' })
  personService.profile = async () => ({ person: { personId: 1 }, currentHousehold: null, currentResidence: null })
  const page = createPage(loadPage('../pages/persons/detail/index'))
  await page.onLoad({ id: 1 })
  assert.equal(page.data.profileLoaded, true)
  assert.equal(page.data.household, null)
  assert.equal(page.data.profileError, '')

  personService.profile = async () => { throw new Error('profile unavailable') }
  const failedPage = createPage(loadPage('../pages/persons/detail/index'))
  await failedPage.onLoad({ id: 1 })
  assert.equal(failedPage.data.profileLoaded, false)
  assert.ok(failedPage.data.profileError)
  personService.detail = originalDetail
  personService.profile = originalProfile
})

test('population detail opens the exact household route without caching sensitive data', () => {
  let route = ''
  global.wx.navigateTo = (options) => { route = options.url }
  const definition = loadPage('../pages/persons/detail/index')
  definition.openHousehold.call({ data: { household: { householdId: 22 } } })
  assert.equal(route, '/pages/households/detail/index?id=22')
  assert.doesNotMatch(source('../pages/persons/detail/index.js'), /setStorage|setStorageSync/)
})
