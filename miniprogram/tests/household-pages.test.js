const test = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')

const householdAdapter = require('../adapters/household')
const householdService = require('../services/household')

function source(relativePath) { return fs.readFileSync(path.resolve(__dirname, relativePath), 'utf8') }
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

global.getApp = () => ({ globalData: { user: { permissions: ['household:view'] } } })
global.wx = { stopPullDownRefresh() {}, showToast() {}, navigateTo() {}, getStorageSync() { return '' } }

test('household member count distinguishes zero, positive and unavailable values', () => {
  assert.deepEqual(householdAdapter.memberCount(0), { activeMemberCount: 0, activeMemberCountAvailable: true, activeMemberCountDisplay: '0 人' })
  assert.equal(householdAdapter.memberCount(3).activeMemberCountDisplay, '3 人')
  for (const value of [null, undefined, '2', NaN, -1, 1.5]) {
    assert.equal(householdAdapter.memberCount(value).activeMemberCountDisplay, '成员数暂不可用')
  }
})

test('household adapter localizes type, status and relationship', () => {
  const row = householdAdapter.normalize({ householdType: 'FAMILY', status: 'PENDING_CANCELLATION', activeMemberCount: 0 })
  assert.equal(row.householdTypeDisplay, '家庭户')
  assert.equal(row.statusDisplay, '待注销')
  assert.equal(row.activeMemberCountDisplay, '0 人')
  assert.equal(householdAdapter.member({ relationship: 'HEAD', status: 'ACTIVE' }).relationshipDisplay, '户主')
  assert.equal(householdAdapter.member({ relationship: 'UNKNOWN_RELATION' }).relationshipDisplay, '关系未登记')
})

test('household member keeps masked identity and safely defaults phone', () => {
  const row = householdAdapter.member({ personName: '李四', idCard: '320***********5678', phone: null, relationship: 'CHILD' })
  assert.equal(row.idCardDisplay, '320***********5678')
  assert.equal(row.phoneDisplay, '未登记')
  assert.equal(row.relationshipDisplay, '子女')
})

test('household list starts once, cleans filters and resets pagination', async () => {
  const original = householdService.list
  const calls = []
  householdService.list = async (params) => {
    calls.push(params)
    return { content: [], totalElements: 0, number: 0, last: true }
  }
  const page = createPage(loadPage('../pages/households/list/index'), { householdNo: ' ', headPersonName: ' 张三 ', householdType: '', status: '', page: 5 })
  await Promise.all([page.onLoad(), page.onLoad()])
  assert.equal(calls.length, 1)
  assert.deepEqual(calls[0], { headPersonName: '张三', page: 0, size: 10 })
  page.setData({ householdNo: 'H001', householdType: 'FAMILY', status: 'ACTIVE', page: 7 })
  await page.reset()
  assert.deepEqual(calls[1], { page: 0, size: 10 })
  householdService.list = original
})

test('household list failure is not rendered as an empty result', async () => {
  const original = householdService.list
  householdService.list = async () => { throw new Error('unavailable') }
  const page = createPage(loadPage('../pages/households/list/index'))
  await page.onLoad()
  assert.ok(page.data.error)
  assert.equal(page.data.totalAvailable, false)
  householdService.list = original
})

test('household list does not request after the last page', async () => {
  const original = householdService.list
  let calls = 0
  householdService.list = async () => { calls += 1; return {} }
  const page = createPage(loadPage('../pages/households/list/index'), { last: true })
  page._authorized = true
  await page.onReachBottom()
  assert.equal(calls, 0)
  householdService.list = original
})

test('household list has no value-or-zero fallback and allows multiline addresses', () => {
  const markup = source('../pages/households/list/index.wxml')
  const styles = source('../pages/households/list/index.wxss')
  assert.doesNotMatch(markup, /activeMemberCount\s*\|\|\s*0/)
  assert.match(markup, /activeMemberCountDisplay/)
  assert.match(markup, /name="chevron-right"/)
  assert.match(styles, /\.address-line[^}]*white-space:\s*normal/s)
  assert.doesNotMatch(styles, /\.address-line[^}]*text-overflow:\s*ellipsis/s)
})

test('household detail distinguishes empty members from member request failure', async () => {
  const originalDetail = householdService.detail
  const originalMembers = householdService.members
  householdService.detail = async () => ({ householdId: 1, householdNo: 'H001', activeMemberCount: 0 })
  householdService.members = async () => []
  const page = createPage(loadPage('../pages/households/detail/index'))
  await page.onLoad({ id: 1 })
  assert.equal(page.data.membersLoaded, true)
  assert.deepEqual(page.data.members, [])
  assert.equal(page.data.membersError, '')

  householdService.members = async () => { throw new Error('members unavailable') }
  const failedPage = createPage(loadPage('../pages/households/detail/index'))
  await failedPage.onLoad({ id: 1 })
  assert.equal(failedPage.data.membersLoaded, false)
  assert.ok(failedPage.data.membersError)
  householdService.detail = originalDetail
  householdService.members = originalMembers
})

test('household detail shows head tag, common states and exact person route', () => {
  const markup = source('../pages/households/detail/index.wxml')
  assert.match(markup, /text="户主"/)
  assert.match(markup, /relationshipDisplay/)
  assert.match(markup, /idCardDisplay/)
  assert.match(markup, /phoneDisplay/)
  assert.match(markup, /membersError/)
  let route = ''
  global.wx.navigateTo = (options) => { route = options.url }
  const definition = loadPage('../pages/households/detail/index')
  definition.openPerson({ currentTarget: { dataset: { id: 9 } } })
  assert.equal(route, '/pages/persons/detail/index?id=9')
})

test('household detail address styles wrap and secondary pages stay outside tab bar', () => {
  const styles = source('../pages/households/detail/index.wxss')
  const app = JSON.parse(source('../app.json'))
  assert.match(styles, /\.field \.long-value[^}]*white-space:\s*normal/s)
  const tabs = app.tabBar.list.map((item) => item.pagePath)
  assert.equal(tabs.includes('pages/households/detail/index'), false)
  assert.equal(tabs.includes('pages/persons/detail/index'), false)
})
