const test = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')

const business = require('../adapters/business')
const handling = require('../adapters/handling')
const { TAB_INDEX, syncTabBar, resetTabBar } = require('../utils/tab-bar')

function source(relativePath) {
  return fs.readFileSync(path.resolve(__dirname, relativePath), 'utf8')
}

function loadComponent(relativePath) {
  let definition
  global.Component = (value) => { definition = value }
  const target = require.resolve(relativePath)
  delete require.cache[target]
  require(target)
  return definition
}

test('native custom tab bar declares exactly four real primary routes', () => {
  const app = JSON.parse(source('../app.json'))
  assert.equal(app.tabBar.custom, true)
  assert.deepEqual(app.tabBar.list.map((item) => item.pagePath), [
    'pages/dashboard/index',
    'pages/business/index',
    'pages/handling/index',
    'pages/profile/index'
  ])
  assert.deepEqual(app.tabBar.list.map((item) => item.text), ['工作台', '业务', '办理', '我的'])
  for (const item of app.tabBar.list) assert.ok(app.pages.includes(item.pagePath))
})

test('custom tab bar uses switchTab for business, handling and profile', () => {
  const definition = loadComponent('../custom-tab-bar/index')
  const calls = []
  global.wx = { switchTab: (options) => calls.push(options.url) }
  const component = { data: Object.assign({}, definition.data) }

  for (const index of [1, 2, 3]) {
    definition.methods.switchTab.call(component, { currentTarget: { dataset: { index } } })
  }
  definition.methods.switchTab.call(component, { currentTarget: { dataset: { index: 0 } } })

  assert.deepEqual(calls, [
    '/pages/business/index',
    '/pages/handling/index',
    '/pages/profile/index'
  ])
  assert.equal(definition.data.selected, 0)
  assert.equal(definition.data.selectedColor, '#1677FF')
  assert.equal(definition.data.color, '#8A94A6')
})

test('custom tab icons exist and bottom safe-area styling is present', () => {
  const definition = loadComponent('../custom-tab-bar/index')
  const { resolveIcon } = require('../utils/icons')
  for (const item of definition.data.list) assert.equal(resolveIcon({ name: item.icon }).renderable, true, item.icon)
  assert.match(source('../custom-tab-bar/index.wxss'), /env\(safe-area-inset-bottom\)/)
  assert.match(source('../app.wxss'), /\.tab-page[\s\S]*safe-area-inset-bottom/)
})

test('login and secondary detail pages are not tab bar routes', () => {
  const app = JSON.parse(source('../app.json'))
  const tabRoutes = app.tabBar.list.map((item) => item.pagePath)
  assert.equal(tabRoutes.includes('pages/login/index'), false)
  for (const route of app.pages.filter((item) => item.includes('/detail/'))) {
    assert.equal(tabRoutes.includes(route), false, route)
  }
})

test('primary pages synchronize selected state and logout can reset it', () => {
  const updates = []
  const page = { getTabBar: () => ({ setData: (value) => updates.push(value) }) }
  syncTabBar(page, 'business')
  syncTabBar(page, 'profile')
  resetTabBar(page)
  assert.deepEqual(TAB_INDEX, { dashboard: 0, business: 1, handling: 2, profile: 3 })
  assert.deepEqual(updates, [{ selected: 1 }, { selected: 3 }, { selected: 0 }])
})

test('business center exposes only existing permission-backed routes', () => {
  assert.deepEqual(
    business.businessEntries({ permissions: ['population:view'] }).map((item) => item.key),
    ['population']
  )
  assert.deepEqual(
    business.businessEntries({ permissions: ['household:view'] }).map((item) => item.key),
    ['household']
  )
  assert.deepEqual(business.businessEntries({ permissions: [] }), [])
  assert.deepEqual(business.BUSINESS_ENTRIES.map((item) => item.url), [
    '/pages/persons/list/index',
    '/pages/households/list/index'
  ])
})

test('handling center exposes only existing permission-backed routes', () => {
  assert.deepEqual(
    handling.handlingEntries({ permissions: ['application:view'] }).map((item) => item.key),
    ['application']
  )
  assert.deepEqual(
    handling.handlingEntries({ permissions: ['approval:view'] }).map((item) => item.key),
    ['approval']
  )
  assert.deepEqual(handling.handlingEntries({ permissions: [] }), [])
  assert.deepEqual(handling.HANDLING_ENTRIES.map((item) => item.url), [
    '/pages/applications/list/index',
    '/pages/approvals/list/index'
  ])
})

test('pending summary preserves a real zero but never invents one', () => {
  assert.deepEqual(handling.normalizePendingSummary({ pendingApprovals: 0 }), {
    available: true,
    value: 0,
    valueText: '0'
  })
  assert.deepEqual(handling.normalizePendingSummary({}), {
    available: false,
    value: null,
    valueText: ''
  })
  assert.equal(handling.normalizePendingSummary({ pendingApprovals: '0' }).available, false)
})

test('new center pages reuse common state components and secondary navigation', () => {
  const businessJson = JSON.parse(source('../pages/business/index.json'))
  const handlingJson = JSON.parse(source('../pages/handling/index.json'))
  const businessSource = source('../pages/business/index.js')
  const handlingSource = source('../pages/handling/index.js')

  assert.equal(businessJson.usingComponents['empty-state'], '/components/empty-state/index')
  assert.equal(handlingJson.usingComponents['loading-state'], '/components/loading-state/index')
  assert.equal(handlingJson.usingComponents['empty-state'], '/components/empty-state/index')
  assert.equal(handlingJson.usingComponents['error-state'], '/components/error-state/index')
  assert.match(businessSource, /wx\.navigateTo\(\{ url \}\)/)
  assert.match(handlingSource, /wx\.navigateTo\(\{ url \}\)/)
  assert.doesNotMatch(`${businessSource}\n${handlingSource}`, /roleCode|roleName/)
})

test('business and handling cards navigate to their exact secondary routes', () => {
  const calls = []
  global.wx = { navigateTo: (options) => calls.push(options.url) }
  let businessPage
  global.Page = (value) => { businessPage = value }
  let target = require.resolve('../pages/business/index')
  delete require.cache[target]
  require(target)
  businessPage.open({ currentTarget: { dataset: { url: business.BUSINESS_ENTRIES[0].url } } })
  businessPage.open({ currentTarget: { dataset: { url: business.BUSINESS_ENTRIES[1].url } } })

  let handlingPage
  global.Page = (value) => { handlingPage = value }
  target = require.resolve('../pages/handling/index')
  delete require.cache[target]
  require(target)
  handlingPage.open({ currentTarget: { dataset: { url: handling.HANDLING_ENTRIES[0].url } } })
  handlingPage.open({ currentTarget: { dataset: { url: handling.HANDLING_ENTRIES[1].url } } })

  assert.deepEqual(calls, [
    '/pages/persons/list/index',
    '/pages/households/list/index',
    '/pages/applications/list/index',
    '/pages/approvals/list/index'
  ])
})

test('business center has no request on first load and shows only real entries', () => {
  let definition
  let requestCount = 0
  global.Page = (value) => { definition = value }
  global.getApp = () => ({
    globalData: {
      user: { userId: 1, permissions: ['population:view', 'household:view'] }
    }
  })
  global.wx = { request: () => { requestCount += 1 } }
  const target = require.resolve('../pages/business/index')
  delete require.cache[target]
  require(target)
  const page = Object.assign({}, definition, {
    data: Object.assign({}, definition.data),
    setData(update) { Object.assign(this.data, update) }
  })

  page.onLoad()
  page.onShow()

  assert.equal(requestCount, 0)
  assert.deepEqual(page.data.entries.map((item) => item.key), ['population', 'household'])
})

test('handling page first load makes one summary request and onShow does not repeat it', async () => {
  const dashboard = require('../services/dashboard')
  const originalOverview = dashboard.overview
  let definition
  let requests = 0
  global.Page = (value) => { definition = value }
  global.getApp = () => ({
    globalData: {
      user: { userId: 1, permissions: ['approval:view'] }
    }
  })
  global.wx = { stopPullDownRefresh() {}, navigateTo() {} }
  dashboard.overview = async () => { requests += 1; return { pendingApprovals: 0 } }
  const target = require.resolve('../pages/handling/index')
  delete require.cache[target]
  require(target)
  const page = Object.assign({}, definition, {
    data: Object.assign({}, definition.data),
    setData(update) { Object.assign(this.data, update) }
  })

  await page.onLoad()
  page.onShow()
  page.onShow()

  assert.equal(requests, 1)
  assert.equal(page.data.pendingSummary.valueText, '0')
  dashboard.overview = originalOverview
})

test('handling summary failure is explicit and does not display a fake zero', async () => {
  const dashboard = require('../services/dashboard')
  const originalOverview = dashboard.overview
  let definition
  global.Page = (value) => { definition = value }
  global.getApp = () => ({
    globalData: {
      user: { userId: 1, permissions: ['approval:view'] }
    }
  })
  global.wx = { stopPullDownRefresh() {}, navigateTo() {} }
  dashboard.overview = async () => { throw new Error('unavailable') }
  const target = require.resolve('../pages/handling/index')
  delete require.cache[target]
  require(target)
  const page = Object.assign({}, definition, {
    data: Object.assign({}, definition.data),
    setData(update) { Object.assign(this.data, update) }
  })

  await page.onLoad()

  assert.ok(page.data.summaryError)
  assert.equal(page.data.pendingSummary.available, false)
  assert.equal(page.data.pendingSummary.valueText, '')
  dashboard.overview = originalOverview
})
