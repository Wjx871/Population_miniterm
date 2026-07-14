export const demoOverview = {
  data: {
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
}

export const demoCharts = {
  data: {
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
      { name: 'MIGRATION_IN', count: 3200 },
      { name: 'MIGRATION_OUT', count: 1850 },
      { name: 'RESIDENCE_PERMIT', count: 1500 },
      { name: 'CANCELLATION', count: 450 },
      { name: 'KEY_POPULATION', count: 120 },
      { name: 'CERTIFICATE', count: 890 }
    ],
    permitStatusDistribution: [
      { name: 'PENDING', count: 250 },
      { name: 'APPROVED', count: 128000 },
      { name: 'REJECTED', count: 120 },
      { name: 'COMPLETED', count: 8500 }
    ],
    registeredPopulationByRegion: [
      { regionCode: '110101', regionName: '东城区', count: 150000 },
      { regionCode: '110102', regionName: '西城区', count: 180000 },
      { regionCode: '110105', regionName: '朝阳区', count: 350000 },
      { regionCode: '110108', regionName: '海淀区', count: 320000 },
      { regionCode: '110106', regionName: '丰台区', count: 200000 },
      { regionCode: '110107', regionName: '石景山区', count: 80000 },
      { regionCode: '110114', regionName: '昌平区', count: 150000 },
      { regionCode: '110115', regionName: '大兴区', count: 160000 }
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
}
