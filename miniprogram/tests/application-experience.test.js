const test = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const adapter = require('../adapters/application')

function source(relativePath) {
  return fs.readFileSync(path.resolve(__dirname, relativePath), 'utf8')
}

test('application display model keeps raw state separate from user copy', () => {
  const approved = adapter.normalize({ status: 'APPROVED' })
  assert.equal(approved.rawStatus, 'APPROVED')
  assert.equal(approved.statusDisplay, '已通过')
  assert.equal(approved.isApproved, true)
  assert.equal(approved.isExecuted, false)
  assert.equal(approved.executionDisplay, '等待业务办理')

  const completed = adapter.normalize({ status: 'COMPLETED' })
  assert.equal(completed.isExecuted, true)
  assert.equal(completed.executionDisplay, '业务已办理完成')
})

test('application next-step copy covers review rejection and completion', () => {
  assert.match(adapter.normalize({ status: 'UNDER_REVIEW' }).nextStepDisplay, /等待审核/)
  assert.match(adapter.normalize({ status: 'REJECTED' }).nextStepDisplay, /审批意见/)
  assert.match(adapter.normalize({ status: 'COMPLETED' }).nextStepDisplay, /办理完成/)
})

test('application progress never treats approval as business completion', () => {
  const progress = adapter.progress(adapter.normalize({ status: 'APPROVED', submittedAt: '2026-07-15T10:00:00' }))
  const execution = progress.find((item) => item.key === 'execution')
  assert.equal(execution.state, 'current')
  assert.equal(execution.result, '请在 Web 端完成专业业务执行')
})

test('application detail business decisions do not depend on localized status text', () => {
  const markup = source('../pages/applications/detail/index.wxml')
  const script = source('../pages/applications/detail/index.js')
  assert.doesNotMatch(`${markup}\n${script}`, /statusDisplay\s*===|statusDisplay\s*!==/)
  assert.match(markup, /办理进度/)
  assert.match(markup, /审批结果/)
  assert.doesNotMatch(markup, /materialTypeDisplay|专业记录版本/)
})

test('application list provides filtered and unfiltered empty guidance', () => {
  const markup = source('../pages/applications/list/index.wxml')
  assert.match(markup, /未找到符合条件的申请/)
  assert.match(markup, /清除筛选条件/)
  assert.match(markup, /当前账号还没有提交过业务申请/)
})

test('application list refreshes once after a detail action changes state', async () => {
  const service = require('../services/application')
  const originalList = service.list
  let definition
  let requests = 0
  global.Page = (value) => { definition = value }
  global.getApp = () => ({ globalData: { user: { permissions: ['application:view'] } } })
  global.wx = { stopPullDownRefresh() {}, navigateTo() {} }
  service.list = async () => { requests += 1; return { content: [], totalElements: 0, number: 0, last: true } }
  const target = require.resolve('../pages/applications/list/index')
  delete require.cache[target]
  require(target)
  const page = Object.assign({}, definition, {
    data: Object.assign({}, definition.data),
    setData(update) { Object.assign(this.data, update) }
  })

  await page.onLoad()
  await page.onShow()
  page._needsRefresh = true
  await Promise.all([page.onShow(), page.onShow()])

  assert.equal(requests, 2)
  service.list = originalList
})

test('withdrawing an application marks its list for refresh', async () => {
  const service = require('../services/application')
  const originalWithdraw = service.withdraw
  let definition
  const list = { route: 'pages/applications/list/index' }
  global.Page = (value) => { definition = value }
  global.getCurrentPages = () => [list, { route: 'pages/applications/detail/index' }]
  global.wx = { showModal: ({ success }) => success({ confirm: true }), showToast() {} }
  service.withdraw = async () => {}
  const target = require.resolve('../pages/applications/detail/index')
  delete require.cache[target]
  require(target)
  const page = Object.assign({}, definition, {
    data: Object.assign({}, definition.data, { id: 1, canWithdraw: true }),
    setData(update) { Object.assign(this.data, update) },
    load: async () => {}
  })

  await page.withdraw()

  assert.equal(list._needsRefresh, true)
  service.withdraw = originalWithdraw
  delete global.getCurrentPages
})
