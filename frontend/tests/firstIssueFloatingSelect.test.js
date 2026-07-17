import test from 'node:test'
import assert from 'node:assert/strict'
import fs from 'node:fs'

const source = (path) => fs.readFileSync(new URL(path, import.meta.url), 'utf8')

test('首次申领选择器只请求可申领的流动登记', () => {
  const dialog = source('../src/views/floating/components/FirstIssueDialog.vue')
  const select = source('../src/components/business/FloatingSelect.vue')

  assert.match(dialog, /:available-for-first-issue="true"/)
  assert.match(select, /if \(props\.availableForFirstIssue\) query\.availableForFirstIssue = true/)
})
