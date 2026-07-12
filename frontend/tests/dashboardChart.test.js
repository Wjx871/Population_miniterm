import test from 'node:test'
import assert from 'node:assert/strict'
import { migrationTrendOption, namedCountOption } from '../src/utils/dashboardChart.js'

test('迁移图空数组可渲染且不修改输入', () => {
  const source = []
  const option = migrationTrendOption(source)
  assert.deepEqual(option.series[0].data, [])
  assert.deepEqual(source, [])
})

test('统计图保留 0', () => {
  const option = namedCountOption([{ label: '有效', value: 0 }])
  assert.deepEqual(option.series[0].data, [0])
})
