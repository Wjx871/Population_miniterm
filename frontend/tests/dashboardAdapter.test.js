import test from 'node:test'
import assert from 'node:assert/strict'
import { normalizeDashboardCharts, normalizeDashboardOverview } from '../src/adapters/dashboard.js'

test('Dashboard overview 保留真实 0，不伪造缺失统计', () => {
  const overview = normalizeDashboardOverview({ registeredPopulation: 0, pendingApprovals: undefined })
  assert.equal(overview.registeredPopulation, 0)
  assert.equal(overview.pendingApprovals, null)
})

test('Dashboard adapter 保留 null 和 false，不将其转为 0', () => {
  const result = normalizeDashboardOverview({ registeredPopulation: null, activeFloatingPopulation: false })
  assert.equal(result.registeredPopulation, null)
  assert.equal(result.activeFloatingPopulation, false)
})

test('Dashboard charts 对非数组回退为空数组且不改写源数组', () => {
  const source = [{ date: '2026-07-01', inCount: 0, outCount: 2 }]
  const charts = normalizeDashboardCharts({ migrationTrend: source, permitStatusDistribution: 'invalid' })
  assert.deepEqual(charts.permitStatusDistribution, [])
  assert.notEqual(charts.migrationTrend, source)
  assert.equal(source[0].inCount, 0)
})
