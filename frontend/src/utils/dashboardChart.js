const palette = ['#1e40af', '#0f766e', '#d97706', '#7c3aed', '#dc2626']

export function migrationTrendOption(points = []) {
  const rows = Array.isArray(points) ? points : []
  return {
    color: palette.slice(0, 2),
    tooltip: { trigger: 'axis' },
    legend: { data: ['迁入', '迁出'], bottom: 0 },
    grid: { left: 42, right: 18, top: 30, bottom: 42 },
    xAxis: { type: 'category', data: rows.map((item) => item.date || '') },
    yAxis: { type: 'value', minInterval: 1 },
    series: [
      { name: '迁入', type: 'line', smooth: true, data: rows.map((item) => item.inCount ?? 0), areaStyle: { opacity: 0.08 } },
      { name: '迁出', type: 'line', smooth: true, data: rows.map((item) => item.outCount ?? 0), areaStyle: { opacity: 0.08 } },
    ],
  }
}

export function namedCountOption(rows = [], type = 'bar') {
  const data = Array.isArray(rows) ? rows : []
  if (type === 'pie') {
    return { color: palette, tooltip: { trigger: 'item' }, legend: { bottom: 0 }, series: [{ type: 'pie', radius: ['42%', '68%'], data: data.map((item) => ({ name: item.label || item.code || '', value: item.value ?? 0 })) }] }
  }
  return { color: [palette[0]], tooltip: { trigger: 'axis' }, grid: { left: 42, right: 18, top: 24, bottom: 42 }, xAxis: { type: 'category', data: data.map((item) => item.label || item.code || ''), axisLabel: { interval: 0, rotate: 20 } }, yAxis: { type: 'value', minInterval: 1 }, series: [{ type: 'bar', barMaxWidth: 34, data: data.map((item) => item.value ?? 0) }] }
}

export function regionRankingOption(rows = []) {
  const data = Array.isArray(rows) ? rows : []
  return { color: [palette[1]], tooltip: { trigger: 'axis' }, grid: { left: 78, right: 18, top: 18, bottom: 22 }, xAxis: { type: 'value', minInterval: 1 }, yAxis: { type: 'category', inverse: true, data: data.map((item) => item.regionName || item.regionCode || '') }, series: [{ type: 'bar', barMaxWidth: 24, data: data.map((item) => item.value ?? 0) }] }
}
