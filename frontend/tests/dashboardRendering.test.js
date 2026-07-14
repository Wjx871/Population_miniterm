import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'

test('工作台动态标题不会把 Vue 插值当作普通属性字符串', async () => {
  const source = await readFile(new URL('../src/views/dashboard/Dashboard.vue', import.meta.url), 'utf8')
  assert.doesNotMatch(source, /label="[^"]*\{\{[^\n]*periodDays/)
  assert.match(source, /:label="migrationInLabel"/)
  assert.match(source, /:label="migrationOutLabel"/)
})

test('工作台统计和事项失败时显示服务端错误而非虚假 0', async () => {
  const dashboard = await readFile(new URL('../src/views/dashboard/Dashboard.vue', import.meta.url), 'utf8')
  const workItems = await readFile(new URL('../src/views/dashboard/components/WorkItemList.vue', import.meta.url), 'utf8')
  assert.match(dashboard, /summaryError/)
  assert.match(dashboard, /getApiErrorMessage/)
  assert.match(workItems, /errorMessage/)
})
