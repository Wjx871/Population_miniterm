function finiteNumber(value) {
  if (value === null || value === undefined || value === false) return value ?? null
  const number = Number(value)
  return Number.isFinite(number) ? number : null
}

function array(value) {
  return Array.isArray(value) ? value : []
}

function namedCounts(value) {
  return array(value).map((item) => ({
    code: item?.code ?? '',
    label: item?.label ?? '',
    value: finiteNumber(item?.value),
  }))
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
    populationStructure: {
      gender: {
        male: finiteNumber(raw?.populationStructure?.gender?.male),
        female: finiteNumber(raw?.populationStructure?.gender?.female),
      },
      ageGroups: namedCounts(raw?.populationStructure?.ageGroups),
    },
    keyBusiness: {
      activeKeyPopulation: finiteNumber(raw?.keyBusiness?.activeKeyPopulation),
      pendingCancellation: finiteNumber(raw?.keyBusiness?.pendingCancellation),
      expiringResidencePermits: finiteNumber(raw?.keyBusiness?.expiringResidencePermits),
      pendingSensitiveExport: finiteNumber(raw?.keyBusiness?.pendingSensitiveExport),
    },
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
    businessScale: namedCounts(raw?.businessScale),
    permitStatusDistribution: namedCounts(raw?.permitStatusDistribution),
    registeredPopulationByRegion: array(raw?.registeredPopulationByRegion).map((item) => ({
      regionCode: item?.regionCode ?? '', regionName: item?.regionName ?? null, value: finiteNumber(item?.value),
    })),
    populationScaleTrend: array(raw?.populationScaleTrend).map((item) => ({
      date: item?.date ?? null,
      registeredPopulation: finiteNumber(item?.registeredPopulation),
      floatingPopulation: finiteNumber(item?.floatingPopulation),
      residencePermits: finiteNumber(item?.residencePermits),
    })),
  }
}
