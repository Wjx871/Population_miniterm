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

  // 待审批黄 / 已通过绿 / 已驳回红 / 已办结蓝
  const colorMap = {
    PENDING: theme.dashboardChartColors[2],
    APPROVED: theme.dashboardChartColors[1],
    REJECTED: theme.dashboardChartColors[3],
    COMPLETED: theme.dashboardChartColors[4]
  }

  const labelMap = {
    PENDING: '待审批',
    APPROVED: '已通过',
    REJECTED: '已驳回',
    COMPLETED: '已办结'
  }

  const chartData = data.map((item) => {
    const code = item.name || item.code || item.label || '未知'
    return {
      name: labelMap[code] || code,
      value: item.count || item.value || 0,
      itemStyle: {
        color: colorMap[code] || theme.dashboardChartColors[0],
        shadowBlur: 12,
        shadowColor: colorMap[code] || theme.dashboardChartColors[0]
      }
    }
  })

  const formatNum = (n) => Number(n || 0).toLocaleString()

  return {
    color: theme.dashboardChartColors,
    tooltip: {
      ...theme.darkTooltip,
      trigger: 'item',
      formatter: (p) => {
        const percent = total > 0 ? ((p.value / total) * 100).toFixed(1) : '0.0'
        return `${p.marker}${p.name}<br/>数量：${formatNum(p.value)}<br/>占比：${percent}%`
      }
    },
    legend: {
      ...theme.darkLegend,
      orient: 'vertical',
      right: 8,
      top: 'center',
      itemGap: 14,
      itemWidth: 10,
      itemHeight: 10,
      formatter: (name) => {
        const target = chartData.find((d) => d.name === name)
        const val = target ? target.value : 0
        const percent = total > 0 ? Math.round((val / total) * 100) : 0
        return `{name|${name}}{value|${formatNum(val)}}{percent|${percent}%}`
      },
      textStyle: {
        rich: {
          name: {
            color: '#e8f6ff',
            fontSize: 13,
            width: 52,
            padding: [0, 8, 0, 0]
          },
          value: {
            color: '#ffffff',
            fontSize: 13,
            fontWeight: 'bold',
            fontFamily: 'Courier New',
            width: 72,
            align: 'right',
            padding: [0, 8, 0, 0]
          },
          percent: {
            color: '#9ad8ff',
            fontSize: 12,
            width: 40,
            align: 'right'
          }
        }
      }
    },
    title: {
      text: `{total|${formatNum(total)}}\n{label|总计}`,
      left: '34%',
      top: '42%',
      textAlign: 'center',
      textStyle: {
        rich: {
          total: {
            fontSize: 18,
            fontWeight: 'bold',
            color: '#ffffff',
            fontFamily: 'Courier New',
            lineHeight: 24,
            textShadowColor: 'rgba(61,240,255,0.55)',
            textShadowBlur: 8
          },
          label: {
            fontSize: 12,
            color: '#a8c8e8',
            lineHeight: 18
          }
        }
      }
    },
    series: [
      {
        type: 'pie',
        radius: ['52%', '72%'],
        center: ['35%', '50%'],
        avoidLabelOverlap: true,
        label: { show: false },
        labelLine: { show: false },
        data: chartData,
        itemStyle: {
          borderWidth: 3,
          borderColor: theme.chartBg,
          borderRadius: 8
        },
        emphasis: {
          scale: true,
          scaleSize: 6,
          itemStyle: {
            shadowBlur: 20,
            shadowColor: 'rgba(61, 240, 255, 0.55)'
          }
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

  const palette = theme.dashboardChartColors

  return {
    color: palette,
    tooltip: {
      ...theme.darkTooltip,
      trigger: 'axis',
      axisPointer: { type: 'shadow' }
    },
    grid: { ...theme.darkGrid, left: 10, top: 10, bottom: 0, right: 48 },
    xAxis: {
      type: 'value',
      show: false
    },
    yAxis: {
      type: 'category',
      inverse: false,
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: { color: '#e2f3ff', fontSize: 13 },
      data: sorted.map((item) => labelMap[item.name || item.code] || item.name || '')
    },
    series: [
      {
        type: 'bar',
        barWidth: 14,
        showBackground: true,
        backgroundStyle: {
          color: 'rgba(255, 255, 255, 0.06)',
          borderRadius: [0, 8, 8, 0]
        },
        itemStyle: {
          borderRadius: [0, 8, 8, 0]
        },
        label: {
          show: true,
          position: 'right',
          color: '#9ef7ff',
          fontFamily: 'Courier New',
          fontWeight: 'bold',
          formatter: (params) => Number(params.value || 0).toLocaleString()
        },
        // 每条业务用不同霓虹色，增强科技感
        data: sorted.map((item, index) => {
          const color = palette[index % palette.length]
          return {
            value: item.count || item.value || 0,
            itemStyle: {
              color: theme.horizontalBarGradient(color, 0.12),
              shadowBlur: 12,
              shadowColor: color
            }
          }
        })
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
