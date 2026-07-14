export const demoOverview = {
  generatedAt: new Date().toISOString(),
  periodDays: 30,
  expiryDays: 30,
  registeredPopulation: 1254300,
  activeFloatingPopulation: 342100,
  activeResidencePermits: 128900,
  pendingApprovals: 42,
  expiringResidencePermits: 156,
  migrationInPeriod: 3200,
  migrationOutPeriod: 1850,
  
  // 以下为真实接口可能缺失，但在演示中需要饱满效果的补充数据
  populationStructure: {
    gender: { male: 51.2, female: 48.8 },
    ageGroups: [
      { label: '0-17岁', value: 18 },
      { label: '18-29岁', value: 25 },
      { label: '30-44岁', value: 30 },
      { label: '45-59岁', value: 17 },
      { label: '60岁以上', value: 10 }
    ]
  },
  keyBusiness: {
    activeKeyPopulation: 850,
    pendingCancellation: 15,
    pendingSensitiveExport: 3
  }
}

export const demoCharts = {
  generatedAt: new Date().toISOString(),
  migrationTrend: Array.from({ length: 30 }, (_, i) => {
    const date = new Date()
    date.setDate(date.getDate() - (29 - i))
    return {
      date: date.toISOString().split('T')[0],
      inCount: Math.floor(Math.random() * 50 + 80),
      outCount: Math.floor(Math.random() * 40 + 50)
    }
  }),
  businessScale: [
    { code: 'MIGRATION_IN', label: '迁入登记', value: 3200 },
    { code: 'MIGRATION_OUT', label: '迁出登记', value: 1850 },
    { code: 'RESIDENCE_PERMIT', label: '居住证办理', value: 1500 },
    { code: 'CANCELLATION', label: '人员注销', value: 450 },
    { code: 'KEY_POPULATION', label: '重点人口', value: 120 },
    { code: 'CERTIFICATE', label: '证明开具', value: 890 }
  ],
  permitStatusDistribution: [
    { code: 'PENDING', label: '待审批', value: 250 },
    { code: 'APPROVED', label: '已审批', value: 128000 },
    { code: 'REJECTED', label: '已驳回', value: 120 },
    { code: 'COMPLETED', label: '已完成', value: 8500 }
  ],
  registeredPopulationByRegion: [
    { regionCode: '110101', regionName: '东城区', value: 150000 },
    { regionCode: '110102', regionName: '西城区', value: 180000 },
    { regionCode: '110105', regionName: '朝阳区', value: 350000 },
    { regionCode: '110108', regionName: '海淀区', value: 320000 },
    { regionCode: '110106', regionName: '丰台区', value: 200000 },
    { regionCode: '110107', regionName: '石景山区', value: 80000 },
    { regionCode: '110114', regionName: '昌平区', value: 150000 },
    { regionCode: '110115', regionName: '大兴区', value: 160000 }
  ],
  // 补充历史规模趋势（若真实接口缺）
  populationScaleTrend: Array.from({ length: 7 }, (_, i) => {
    const date = new Date()
    date.setDate(date.getDate() - (6 - i))
    return {
      date: date.toISOString().split('T')[0],
      registeredPopulation: 1250000 + i * 500,
      floatingPopulation: 340000 + i * 300,
      residencePermits: 128000 + i * 150
    }
  })
}
