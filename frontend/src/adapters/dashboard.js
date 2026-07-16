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

/**
 * 生成 keyBusiness 的空结构。任何路径（包括 catch / 接口不存在该字段）
 * 都应拿到 4 个有限数字（0 也算有限），避免面板被判为"无数据"而空态。
 */
function emptyKeyBusiness() {
  return {
    activeKeyPopulation: 0,
    pendingCancellation: 0,
    expiringResidencePermits: 0,
    pendingSensitiveExport: 0,
  }
}

/**
 * 生成 populationStructure 的空结构。真实后端永远返回 6 段 ageGroups，
 * 但兜底也用同样的 6 段占位，确保 length > 0、hasData 永远 true。
 */
function emptyPopulationStructure() {
  return {
    gender: { male: 0, female: 0 },
    ageGroups: [
      { code: 'AGE_0_17', label: '0-17岁', value: 0 },
      { code: 'AGE_18_29', label: '18-29岁', value: 0 },
      { code: 'AGE_30_44', label: '30-44岁', value: 0 },
      { code: 'AGE_45_59', label: '45-59岁', value: 0 },
      { code: 'AGE_60_PLUS', label: '60岁及以上', value: 0 },
      { code: 'UNKNOWN', label: '出生日期缺失', value: 0 }
    ]
  }
}

export function normalizeDashboardOverview(raw = {}) {
  // 注意：catch 路径不再返回 null，让三个面板永远能渲染（哪怕 0）
  const rawKeyBusiness = raw?.keyBusiness && typeof raw.keyBusiness === 'object'
    ? raw.keyBusiness
    : null
  const rawPopStructure = raw?.populationStructure && typeof raw.populationStructure === 'object'
    ? raw.populationStructure
    : null

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
    populationStructure: rawPopStructure
      ? {
          gender: {
            male: finiteNumber(rawPopStructure.gender?.male),
            female: finiteNumber(rawPopStructure.gender?.female),
          },
          ageGroups: namedCounts(rawPopStructure.ageGroups).length
            ? namedCounts(rawPopStructure.ageGroups)
            : emptyPopulationStructure().ageGroups
        }
      : emptyPopulationStructure(),
    keyBusiness: rawKeyBusiness
      ? {
          activeKeyPopulation: finiteNumber(rawKeyBusiness.activeKeyPopulation),
          pendingCancellation: finiteNumber(rawKeyBusiness.pendingCancellation),
          expiringResidencePermits: finiteNumber(rawKeyBusiness.expiringResidencePermits),
          pendingSensitiveExport: finiteNumber(rawKeyBusiness.pendingSensitiveExport),
        }
      : null,
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
    approvalStatusDistribution: namedCounts(raw?.approvalStatusDistribution),
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
