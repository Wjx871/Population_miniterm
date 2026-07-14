export const dashboardChartColors = ['#29d7ff', '#00ffaa', '#fcd34d', '#f87171', '#3b82f6', '#8b5cf6'];

const axisLineColor = 'rgba(132, 188, 234, 0.35)';
const splitLineColor = 'rgba(80, 151, 216, 0.13)';
const axisLabelColor = '#8fb5d4';

export const darkTooltip = {
  backgroundColor: 'rgba(2, 15, 40, 0.94)',
  borderColor: 'rgba(41, 215, 255, 0.55)',
  textStyle: { color: '#edf8ff' },
  extraCssText: 'box-shadow: 0 0 18px rgba(0,136,255,0.18);'
};

export const darkLegend = {
  textStyle: { color: axisLabelColor },
  itemWidth: 10,
  itemHeight: 10,
  icon: 'circle'
};

export const darkCategoryAxis = {
  type: 'category',
  axisLine: { lineStyle: { color: axisLineColor } },
  axisTick: { show: false },
  axisLabel: { color: axisLabelColor, margin: 12 },
  splitLine: { show: false }
};

export const darkValueAxis = {
  type: 'value',
  axisLine: { show: false },
  axisTick: { show: false },
  axisLabel: { color: axisLabelColor },
  splitLine: {
    show: true,
    lineStyle: { type: 'dashed', color: splitLineColor }
  }
};

export const darkGrid = {
  top: 40,
  bottom: 20,
  left: 20,
  right: 20,
  containLabel: true
};
