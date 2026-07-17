import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'

test('approved application hint reflects execution state instead of always reporting no permission', async () => {
  const [detailSource, actionBarSource] = await Promise.all([
    readFile(new URL('../src/views/applications/ApplicationDetail.vue', import.meta.url), 'utf8'),
    readFile(new URL('../src/components/business/ApplicationActionBar.vue', import.meta.url), 'utf8')
  ])

  assert.match(detailSource, /:approved-hint="approvedActionHint"/)
  assert.match(detailSource, /if \(canExecute\.value\)/)
  assert.match(detailSource, /当前账号具备执行权限/)
  assert.match(detailSource, /尚未满足执行条件/)
  assert.match(actionBarSource, /\{\{ approvedHint \}\}/)
  assert.doesNotMatch(actionBarSource, /<span[^>]*>审批已通过，等待具备执行权限的管理员处理。<\/span>/)
})
