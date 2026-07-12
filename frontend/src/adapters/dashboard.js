function finiteNumber(value) {
  if (value === null || value === undefined || value === false) return value ?? null
  const number = Number(value)
  return Number.isFinite(number) ? number : null
}

function array(value) {
  return Array.isArray(value) ? value : []
}

export function normalizeDashboardOverview(raw = {}) {
  return {
    generatedAt: raw?.generatedAt ?? null,
    periodDays: finiteNumber(raw?.periodDays),
    expiryDays: finiteNumber(raw?.expiryDays),
    registeredPopulation: finiteNumber(raw?.registeredPopulation),
    activeFloatingPopulation: finiteNumber(raw?.activeFloatingPopulation),
    activeResidencePermits: finiteNumber(raw?.activeResidencePermits),
    pendingApprovals: finiteNumber(raw?.pendingApprovals),
    expiringResidencePermits: finiteNumber(raw?.expiringResidencePermits),
    migrationInPeriod: finiteNumber(raw?.migrationInPeriod),
    migrationOutPeriod: finiteNumber(raw?.migrationOutPeriod),
  }
}

export function normalizeDashboardCharts(raw = {}) {
  return {
    generatedAt: raw?.generatedAt ?? null,
    migrationTrend: array(raw?.migrationTrend).map((item) => ({
      date: item?.date ?? null,
      inCount: finiteNumber(item?.inCount),
      outCount: finiteNumber(item?.outCount),
    })),
    businessScale: array(raw?.businessScale).map((item) => ({ code: item?.code ?? '', label: item?.label ?? '', value: finiteNumber(item?.value) })),
    permitStatusDistribution: array(raw?.permitStatusDistribution).map((item) => ({ code: item?.code ?? '', label: item?.label ?? '', value: finiteNumber(item?.value) })),
    registeredPopulationByRegion: array(raw?.registeredPopulationByRegion).map((item) => ({
      regionCode: item?.regionCode ?? '', regionName: item?.regionName ?? null, value: finiteNumber(item?.value),
    })),
  }
}
