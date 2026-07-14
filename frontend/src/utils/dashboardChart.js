import * as theme from '../views/dashboard/options/chartTheme.js'

// 近30日迁入迁出趋势
export function migrationTrendOption(points = []) {
  const rows = Array.isArray(points) ? points : []
  const inColor = theme.accentCyan
  const outColor = theme.accentBlue

  return {
    color: [inColor, outColor],
    tooltip: {
      ...theme.darkTooltip,
      trigger: 'axis'
    },
    legend: {
      ...theme.darkLegend,
      data: ['迁入', '迁出'],
      top: 0,
      right: 0,
      icon: 'roundRect'
    },
    grid: { ...theme.darkGrid, top: 40, bottom: 20 },
    xAxis: {
      ...theme.darkCategoryAxis,
      data: rows.map((item) => item.date || '')
    },
    yAxis: {
      ...theme.darkValueAxis,
      minInterval: 1
    },
    series: [
      {
        name: '迁入',
        type: 'line',
        smooth: 0.4,
        symbol: 'emptyCircle',
        symbolSize: 6,
        showSymbol: false,
        lineStyle: {
          width: 3,
          shadowBlur: 10,
          shadowColor: 'rgba(31, 228, 255, 0.75)'
        },
        data: rows.map((item) => item.inCount ?? 0),
        areaStyle: {
          color: theme.areaGradient(inColor, 0.4)
        }
      },
      {
        name: '迁出',
        type: 'line',
        smooth: 0.4,
        symbol: 'emptyCircle',
        symbolSize: 6,
        showSymbol: false,
        lineStyle: {
          width: 3,
          shadowBlur: 10,
          shadowColor: 'rgba(47, 123, 255, 0.75)'
        },
        data: rows.map((item) => item.outCount ?? 0),
        areaStyle: {
          color: theme.areaGradient(outColor, 0.35)
        }
      }
    ]
  }
}

// 审批状态环形图
export function approvalStatusOption(rows = []) {
  const data = Array.isArray(rows) ? rows : []
  const total = data.reduce((sum, item) => sum + (item.count || item.value || 0), 0)

  // 黄、绿、红、蓝
  const colorMap = {
    PENDING: theme.dashboardChartColors[2],
    APPROVED: theme.dashboardChartColors[1],
    REJECTED: theme.dashboardChartColors[3],
    COMPLETED: theme.dashboardChartColors[4]
  }

  const chartData = data.map((item) => {
    return {
      name: item.name || item.code || item.label || '未知',
      value: item.count || item.value || 0,
      itemStyle: { color: colorMap[item.name] || theme.dashboardChartColors[0] }
    }
  })

  const labelMap = {
    PENDING: '待审批',
    APPROVED: '已通过',
    REJECTED: '已驳回',
    COMPLETED: '已办结'
  }

  chartData.forEach((d) => {
    if (labelMap[d.name]) d.name = labelMap[d.name]
  })

  return {
    tooltip: { ...theme.darkTooltip, trigger: 'item' },
    legend: {
      ...theme.darkLegend,
      orient: 'vertical',
      right: '5%',
      top: 'center',
      itemGap: 16,
      formatter: (name) => {
        const target = chartData.find((d) => d.name === name)
        const val = target ? target.value : 0
        const percent = total > 0 ? Math.round((val / total) * 100) : 0
        return `{name|${name}}  {value|${val}}  {percent|${percent}%}`
      },
      textStyle: {
        rich: {
          name: { color: theme.dashboardChartColors[2], fontSize: 13, width: 50 },
          value: { color: '#fff', fontSize: 14, fontWeight: 'bold', width: 40 },
          percent: { color: theme.dashboardChartColors[5], fontSize: 13 }
        }
      }
    },
    series: [
      {
        type: 'pie',
        radius: ['55%', '75%'],
        center: ['35%', '50%'],
        avoidLabelOverlap: false,
        label: { show: false },
        labelLine: { show: false },
        data: chartData,
        itemStyle: {
          borderWidth: 4,
          borderColor: theme.chartBg,
          borderRadius: 10,
          shadowBlur: 10,
          shadowColor: 'rgba(0, 0, 0, 0.5)'
        }
      }
    ]
  }
}

// 业务类型占比条形图
export function businessTypeShareOption(rows = []) {
  const data = Array.isArray(rows) ? rows : []
  // 取前 6 个
  const sorted = [...data]
    .sort((a, b) => (b.count || b.value || 0) - (a.count || a.value || 0))
    .slice(0, 6)
  sorted.reverse() // ECharts horizontal bar renders bottom to top

  const labelMap = {
    MIGRATION_IN: '迁入登记',
    MIGRATION_OUT: '迁出登记',
    RESIDENCE_PERMIT: '居住证业务',
    CANCELLATION: '注销申请',
    KEY_POPULATION: '重点人口',
    CERTIFICATE: '证件管理',
    FLOATING_POPULATION: '流动人口'
  }

  const barColor = theme.dashboardChartColors[0]

  return {
    color: [barColor],
    tooltip: {
      ...theme.darkTooltip,
      trigger: 'axis',
      axisPointer: { type: 'shadow' }
    },
    grid: { ...theme.darkGrid, left: 10, top: 10, bottom: 0 },
    xAxis: {
      type: 'value',
      show: false
    },
    yAxis: {
      type: 'category',
      inverse: false,
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: { color: theme.darkCategoryAxis.axisLabel.color, fontSize: 13 },
      data: sorted.map((item) => labelMap[item.name || item.code] || item.name || '')
    },
    series: [
      {
        type: 'bar',
        barWidth: 12,
        showBackground: true,
        backgroundStyle: {
          color: 'rgba(255, 255, 255, 0.05)',
          borderRadius: [0, 6, 6, 0]
        },
        itemStyle: {
          borderRadius: [0, 6, 6, 0],
          color: theme.horizontalBarGradient(barColor, 0.1),
          shadowBlur: 8,
          shadowColor: 'rgba(31, 228, 255, 0.45)'
        },
        label: {
          show: true,
          position: 'right',
          color: barColor,
          fontFamily: 'Courier New',
          fontWeight: 'bold',
          formatter: (params) => params.value
        },
        data: sorted.map((item) => item.count || item.value || 0)
      }
    ]
  }
}

// 底部规模对比图
export function populationScaleOption(trendData = []) {
  const rows = Array.isArray(trendData) ? trendData : []
  const colors = [
    theme.dashboardChartColors[0],
    theme.dashboardChartColors[1],
    theme.dashboardChartColors[2]
  ]

  return {
    color: colors,
    tooltip: {
      ...theme.darkTooltip,
      trigger: 'axis',
      axisPointer: { type: 'shadow' }
    },
    legend: {
      ...theme.darkLegend,
      data: ['户籍人口', '流动人口', '有效居住证'],
      top: 0,
      icon: 'circle'
    },
    grid: { ...theme.darkGrid, top: 40, bottom: 20 },
    xAxis: {
      ...theme.darkCategoryAxis,
      data: rows.map((r) => r.date || '当前')
    },
    yAxis: {
      ...theme.darkValueAxis,
      minInterval: 1
    },
    series: [
      {
        name: '户籍人口',
        type: 'bar',
        barWidth: 14,
        itemStyle: {
          borderRadius: [4, 4, 0, 0],
          color: theme.verticalBarGradient(colors[0]),
          shadowBlur: 8,
          shadowColor: 'rgba(31, 228, 255, 0.4)'
        },
        data: rows.map((r) => r.registeredPopulation || 0)
      },
      {
        name: '流动人口',
        type: 'bar',
        barWidth: 14,
        itemStyle: {
          borderRadius: [4, 4, 0, 0],
          color: theme.verticalBarGradient(colors[1]),
          shadowBlur: 8,
          shadowColor: 'rgba(57, 229, 140, 0.4)'
        },
        data: rows.map((r) => r.floatingPopulation || 0)
      },
      {
        name: '有效居住证',
        type: 'bar',
        barWidth: 14,
        itemStyle: {
          borderRadius: [4, 4, 0, 0],
          color: theme.verticalBarGradient(colors[2]),
          shadowBlur: 8,
          shadowColor: 'rgba(255, 209, 102, 0.4)'
        },
        data: rows.map((r) => r.residencePermits || 0)
      }
    ]
  }
}

// Legacy options for old components (kept to prevent breakages)
export function namedCountOption(rows = [], type = 'bar') {
  const data = Array.isArray(rows) ? rows : []
  return { series: [{ type: type === 'line' ? 'line' : 'bar', data: data.map((item) => item.value ?? 0) }] }
}

export function regionRankingOption(rows = []) {
  const data = Array.isArray(rows) ? rows : []
  return { series: [{ type: 'bar', data: data.map((item) => item.value ?? 0) }] }
}
