import assert from 'node:assert/strict'
import test from 'node:test'

import { goBackOrFallback } from '../src/utils/navigation.js'

test('站内历史存在时返回上一页', () => {
  const originalWindow = globalThis.window
  globalThis.window = { history: { state: { back: '/approvals' } } }
  const router = { backCalls: 0, replaceCalls: [], back() { this.backCalls += 1 }, replace(path) { this.replaceCalls.push(path) } }

  try {
    goBackOrFallback(router, '/applications')
    assert.equal(router.backCalls, 1)
    assert.deepEqual(router.replaceCalls, [])
  } finally {
    globalThis.window = originalWindow
  }
})

test('无站内历史时返回明确的业务列表页', () => {
  const originalWindow = globalThis.window
  globalThis.window = { history: { state: null } }
  const router = { backCalls: 0, replaceCalls: [], back() { this.backCalls += 1 }, replace(path) { this.replaceCalls.push(path) } }

  try {
    goBackOrFallback(router, '/applications')
    assert.equal(router.backCalls, 0)
    assert.deepEqual(router.replaceCalls, ['/applications'])
  } finally {
    globalThis.window = originalWindow
  }
})
