const test = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')

const { resolveStatus } = require('../utils/status')
const { resolveErrorState } = require('../utils/error-state')

function loadComponent(relativePath) {
  let definition
  global.Component = (value) => { definition = value }
  const target = require.resolve(relativePath)
  delete require.cache[target]
  require(target)
  return definition
}

function source(relativePath) {
  return fs.readFileSync(path.resolve(__dirname, relativePath), 'utf8')
}

test('status-tag maps active state to success', () => {
  assert.deepEqual(resolveStatus({ status: 'ACTIVE' }), { text: '有效', type: 'success', size: 'medium' })
})

test('status-tag maps pending approval to warning', () => {
  assert.deepEqual(resolveStatus({ status: 'PENDING_APPROVAL' }), { text: '待审批', type: 'warning', size: 'medium' })
})

test('status-tag maps rejected state to danger', () => {
  assert.deepEqual(resolveStatus({ status: 'REJECTED' }), { text: '已驳回', type: 'danger', size: 'medium' })
})

test('status-tag displays pending cancellation in Chinese', () => {
  assert.equal(resolveStatus({ text: 'PENDING_CANCELLATION' }).text, '待注销')
})

test('status-tag safely handles an unknown state', () => {
  assert.deepEqual(resolveStatus({ status: 'NEW_BACKEND_STATE' }), { text: '未知状态', type: 'info', size: 'medium' })
})

test('status-tag never renders a known English enum directly', () => {
  for (const status of ['ACTIVE', 'PENDING', 'UNDER_REVIEW', 'REJECTED', 'CANCELLED']) {
    assert.doesNotMatch(resolveStatus({ text: status }).text, /^[A-Z_]+$/)
  }
})

test('empty-state supports title and description properties', () => {
  const definition = loadComponent('../components/empty-state/index')
  assert.equal(definition.properties.title.value, '')
  assert.equal(definition.properties.description.value, '')
  const wxml = source('../components/empty-state/index.wxml')
  assert.match(wxml, /title \|\| text \|\| '暂无数据'/)
  assert.match(wxml, /\{\{description\}\}/)
})

test('empty-state action emits an action event', () => {
  const definition = loadComponent('../components/empty-state/index')
  const events = []
  definition.methods.action.call({ data: { actionText: '重新查询' }, triggerEvent: (name) => events.push(name) })
  assert.deepEqual(events, ['action'])
})

test('empty-state without an action does not emit or render an empty button', () => {
  const definition = loadComponent('../components/empty-state/index')
  const events = []
  definition.methods.action.call({ data: { actionText: '' }, triggerEvent: (name) => events.push(name) })
  assert.deepEqual(events, [])
  assert.match(source('../components/empty-state/index.wxml'), /wx:if="\{\{actionText\}\}"/)
})

test('error-state maps 403 to forbidden', () => {
  assert.equal(resolveErrorState({ statusCode: 403 }).title, '无权访问')
})

test('error-state maps 404 to not found', () => {
  assert.equal(resolveErrorState({ statusCode: 404 }).title, '记录不存在')
})

test('error-state maps 409 to conflict', () => {
  assert.equal(resolveErrorState({ statusCode: 409 }).title, '数据状态已发生变化')
})

test('error-state maps 500 to server failure', () => {
  assert.equal(resolveErrorState({ statusCode: 500 }).title, '系统服务异常')
})

test('error-state suppresses backend implementation details', () => {
  const state = resolveErrorState({ statusCode: 500, message: 'java.sql.SQLException: SELECT * FROM users' })
  assert.equal(state.description, '服务暂时不可用，请稍后重试')
})

test('error-state infers server failure from the existing message-only API', () => {
  assert.equal(resolveErrorState({ message: '系统服务异常，请稍后重试' }).type, 'server')
})

test('error-state retry emits a retry event', () => {
  const definition = loadComponent('../components/error-state/index')
  const events = []
  definition.methods.retry.call({ data: { showRetry: true }, triggerEvent: (name) => events.push(name) })
  assert.deepEqual(events, ['retry'])
})

test('sensitive-text renders the server-provided masked value', () => {
  const definition = loadComponent('../components/sensitive-text/index')
  assert.equal(definition.properties.value.value, '—')
  assert.match(source('../components/sensitive-text/index.wxml'), /\{\{value \|\| '—'\}\}/)
})

test('sensitive-text wraps long content without storing a second value', () => {
  const definition = loadComponent('../components/sensitive-text/index')
  assert.deepEqual(definition.data, { expanded: false })
  const wxss = source('../components/sensitive-text/index.wxss')
  assert.match(wxss, /overflow-wrap: anywhere/)
  assert.match(wxss, /word-break: break-word/)
})

test('loading-state exposes default and custom text', () => {
  const definition = loadComponent('../components/loading-state/index')
  assert.equal(definition.properties.text.value, '加载中…')
  assert.match(source('../components/loading-state/index.wxml'), /\{\{text\}\}/)
})

test('loading-state uses CSS animation without timers', () => {
  const javascript = source('../components/loading-state/index.js')
  assert.doesNotMatch(javascript, /setInterval|setTimeout/)
  assert.match(source('../components/loading-state/index.wxss'), /@keyframes loading-spin/)
})
