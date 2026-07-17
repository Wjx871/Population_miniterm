const test = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const adapter = require('../adapters/application')
const approvalService = require('../services/approval')

function source(relativePath) {
  return fs.readFileSync(path.resolve(__dirname, relativePath), 'utf8')
}

test('approval cards use task-specific actions and localized states', () => {
  const pending = adapter.approval({ status: 'PENDING', submittedAt: new Date().toISOString() })
  const processed = adapter.approval({ status: 'APPROVED' })
  assert.equal(pending.actionDisplay, '进入审批')
  assert.equal(pending.statusDisplay, '待审批')
  assert.equal(processed.actionDisplay, '查看结果')
  assert.equal(processed.statusDisplay, '已通过')
})

test('approval list initial lifecycle sends one request and has no automatic onShow reload', async () => {
  let definition
  let requests = 0
  const originalPending = approvalService.pending
  global.Page = (value) => { definition = value }
  global.getApp = () => ({ globalData: { user: { permissions: ['approval:view'] } } })
  global.wx = { stopPullDownRefresh() {}, showToast() {}, navigateTo() {} }
  approvalService.pending = async () => { requests += 1; return [] }
  const target = require.resolve('../pages/approvals/list/index')
  delete require.cache[target]
  require(target)
  const page = Object.assign({}, definition, {
    data: Object.assign({}, definition.data),
    setData(update) { Object.assign(this.data, update) }
  })

  await Promise.all([page.onLoad(), page.onLoad()])

  assert.equal(requests, 1)
  assert.equal(page.onShow, undefined)
  approvalService.pending = originalPending
})

test('approval rejection requires a reason before submission', async () => {
  let definition
  const toasts = []
  global.Page = (value) => { definition = value }
  global.getApp = () => ({ globalData: { user: { permissions: ['approval:view', 'approval:handle'] } } })
  global.wx = { showToast: (options) => toasts.push(options.title), showModal() {} }
  const target = require.resolve('../pages/approvals/detail/index')
  delete require.cache[target]
  require(target)
  const page = Object.assign({}, definition, {
    data: Object.assign({}, definition.data, { canHandle: true, detail: { status: 'PENDING' }, comment: '' }),
    setData(update) { Object.assign(this.data, update) }
  })

  await page.decide({ currentTarget: { dataset: { action: 'reject' } } })
  assert.equal(toasts.at(-1), '请填写驳回原因')
})

test('approval detail explains execution boundary and refreshes state conflicts', () => {
  const markup = source('../pages/approvals/detail/index.wxml')
  const script = source('../pages/approvals/detail/index.js')
  assert.match(markup, /审批通过仅代表审核完成，相关业务仍需继续办理/)
  assert.match(markup, /驳回时请填写具体原因/)
  assert.match(script, /事项状态已更新，已为你刷新/)
  assert.match(script, /if \(error\.statusCode === 409\) await this\.load\(\)/)
})

test('approval pages do not expose material internals or technical status copy', () => {
  const markup = `${source('../pages/approvals/list/index.wxml')}\n${source('../pages/approvals/detail/index.wxml')}`
  assert.doesNotMatch(markup, /materialTypeDisplay|数据库|Redis|缓存模式|后端|HTTP|API/)
  assert.match(markup, /\{\{item\.actionDisplay\}\}/)
})
