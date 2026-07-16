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

test('Dashboard adapter 保留真实模式补充面板的接口数据', () => {
  const overview = normalizeDashboardOverview({
    populationStructure: {
      gender: { male: 51.2, female: 48.8 },
      ageGroups: [{ code: 'AGE_18_29', label: '18-29岁', value: 12 }],
    },
    keyBusiness: { activeKeyPopulation: 3, pendingCancellation: 2 },
  })
  const charts = normalizeDashboardCharts({
    populationScaleTrend: [{ date: '2026-07-12', registeredPopulation: 2, floatingPopulation: 3, residencePermits: 4 }],
  })

  assert.equal(overview.populationStructure.gender.male, 51.2)
  assert.equal(overview.populationStructure.ageGroups[0].value, 12)
  assert.equal(overview.keyBusiness.activeKeyPopulation, 3)
  assert.deepEqual(charts.populationScaleTrend, [
    { date: '2026-07-12', registeredPopulation: 2, floatingPopulation: 3, residencePermits: 4 },
  ])
})
