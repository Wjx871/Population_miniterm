const test = require('node:test')
const assert = require('node:assert/strict')

const dashboardAdapter = require('../adapters/dashboard')
const authAdapter = require('../adapters/auth')
const dashboardService = require('../services/dashboard')

test('dashboard preserves a real zero metric', () => {
  const metrics = dashboardAdapter.normalizeMetrics({ registeredPopulation: 0 }, null)
  const population = metrics.find((item) => item.key === 'population')
  assert.deepEqual({ value: population.value, valueText: population.valueText, available: population.available }, { value: 0, valueText: '0', available: true })
  assert.equal(population.url, '/pages/persons/list/index')
})

test('dashboard does not convert null or undefined metrics to zero', () => {
  const metrics = dashboardAdapter.normalizeMetrics({ registeredPopulation: null }, undefined)
  assert.equal(metrics.find((item) => item.key === 'population').valueText, '暂无')
  assert.equal(metrics.find((item) => item.key === 'households').valueText, '暂无')
})

test('dashboard maps backend and application metrics to their display models', () => {
  const metrics = dashboardAdapter.normalizeMetrics({
    registeredPopulation: 101,
    pendingApprovals: 2,
    migrationInPeriod: 3,
    migrationOutPeriod: 4,
    expiringResidencePermits: 5
  }, 6, 7)
  assert.deepEqual(Object.fromEntries(metrics.map((item) => [item.key, item.value])), {
    pending: 2,
    processing: 7,
    expiringPermits: 5,
    population: 101,
    households: 6,
    migrationIn: 3,
    migrationOut: 4
  })
})

test('dashboard entries are filtered only by permission codes', () => {
  const viewer = dashboardAdapter.dashboardEntries({ permissions: ['population:view'] })
  assert.deepEqual(viewer.map((item) => item.key), ['population'])
  const approver = dashboardAdapter.dashboardEntries({ permissions: ['approval:view'] })
  assert.deepEqual(approver.map((item) => item.key), ['approval'])
})

test('dashboard entry order remains stable for a fully authorized user', () => {
  const entries = dashboardAdapter.dashboardEntries({ permissions: ['population:view', 'household:view', 'application:view', 'approval:view'] })
  assert.deepEqual(entries.map((item) => item.key), ['population', 'household', 'application', 'approval'])
})

test('dashboard user model localizes role and safely defaults department', () => {
  const user = authAdapter.normalizeUser({ roleCode: 'APPROVER' })
  assert.equal(user.roleDisplay, '审批人员')
  assert.equal(user.departmentDisplay, '未配置部门')
  assert.equal(authAdapter.normalizeUser({ roleCode: 'UNKNOWN_ROLE' }).roleDisplay, '未配置角色')
  assert.equal(authAdapter.normalizeUser({ roleCode: 'UNKNOWN_ROLE', roleName: 'UNKNOWN_ROLE' }).roleDisplay, '未配置角色')
})

test('dashboard health failure never appears healthy', () => {
  assert.deepEqual(dashboardAdapter.normalizeHealth(null), { available: false, text: '状态暂不可用', type: 'neutral' })
  assert.deepEqual(dashboardAdapter.normalizeHealth({ database: 'UP' }), { available: true, text: '系统运行正常', type: 'success' })
})

let pageDefinition
let requestCounts
let navigated

global.Page = (definition) => { pageDefinition = definition }
global.getApp = () => ({
  globalData: {
    user: {
      userId: 1,
      realName: '审核人员',
      roleCode: 'APPROVER',
      permissions: ['statistics:view', 'household:view', 'approval:view']
    }
  }
})
global.wx = {
  getStorageSync() { return '' },
  stopPullDownRefresh() {},
  navigateTo(options) { navigated = options.url }
}

require('../pages/dashboard/index')

function createPage() {
  requestCounts = { overview: 0, households: 0 }
  navigated = ''
  dashboardService.overview = async () => { requestCounts.overview += 1; return { registeredPopulation: 0 } }
  dashboardService.householdTotal = async () => { requestCounts.households += 1; return 0 }
  return Object.assign({}, pageDefinition, {
    data: Object.assign({}, pageDefinition.data),
    setData(update) { Object.assign(this.data, update) }
  })
}

test('dashboard refresh guard prevents duplicate requests', async () => {
  const page = createPage()
  const first = page.onShow()
  const duplicate = page.onShow()
  await Promise.all([first, duplicate])
  assert.deepEqual(requestCounts, { overview: 1, households: 1 })
})

test('dashboard metric failure does not create fake zero values', async () => {
  const page = createPage()
  dashboardService.overview = async () => { requestCounts.overview += 1; throw new Error('unavailable') }
  dashboardService.householdTotal = async () => { requestCounts.households += 1; throw new Error('unavailable') }
  await page.onShow()
  assert.ok(page.data.metricsError)
  assert.equal(page.data.metrics.every((item) => item.available === false), true)
})

test('dashboard shortcut opens its existing route', () => {
  const page = createPage()
  page.open({ currentTarget: { dataset: { url: '/pages/approvals/list/index' } } })
  assert.equal(navigated, '/pages/approvals/list/index')
})
