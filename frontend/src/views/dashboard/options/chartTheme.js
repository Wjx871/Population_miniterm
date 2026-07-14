/** 大屏 ECharts 主题单一真相源 */

export const dashboardChartColors = [
  '#1fe4ff', // cyan
  '#39e58c', // green
  '#ffd166', // yellow
  '#ff6b6b', // red
  '#2f7bff', // blue
  '#a66cff'  // purple
];

export const accentCyan = '#1fe4ff';
export const accentBlue = '#2f7bff';
export const accentGreen = '#39e58c';
export const accentYellow = '#ffd166';
export const accentRed = '#ff6b6b';
export const chartBg = '#020d22';

const axisLineColor = 'rgba(132, 188, 234, 0.35)';
const splitLineColor = 'rgba(80, 151, 216, 0.13)';
const axisLabelColor = '#9db8d4';

export const darkTooltip = {
  backgroundColor: 'rgba(2, 15, 40, 0.94)',
  borderColor: 'rgba(31, 228, 255, 0.55)',
  borderWidth: 1,
  textStyle: { color: '#f4fbff' },
  extraCssText: 'box-shadow: 0 0 18px rgba(0,136,255,0.18); backdrop-filter: blur(4px);'
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

/** 垂直柱渐变 */
export function verticalBarGradient(topColor, bottomAlpha = 0.1) {
  return {
    type: 'linear',
    x: 0,
    y: 0,
    x2: 0,
    y2: 1,
    colorStops: [
      { offset: 0, color: topColor },
      { offset: 1, color: hexToRgba(topColor, bottomAlpha) }
    ]
  };
}

/** 水平条渐变 */
export function horizontalBarGradient(endColor, startAlpha = 0.1) {
  return {
    type: 'linear',
    x: 0,
    y: 0,
    x2: 1,
    y2: 0,
    colorStops: [
      { offset: 0, color: hexToRgba(endColor, startAlpha) },
      { offset: 1, color: endColor }
    ]
  };
}

/** 面积渐变 */
export function areaGradient(color, topAlpha = 0.4) {
  return {
    type: 'linear',
    x: 0,
    y: 0,
    x2: 0,
    y2: 1,
    colorStops: [
      { offset: 0, color: hexToRgba(color, topAlpha) },
      { offset: 1, color: hexToRgba(color, 0.01) }
    ]
  };
}

function hexToRgba(hex, alpha) {
  const raw = hex.replace('#', '');
  const full = raw.length === 3
    ? raw.split('').map((c) => c + c).join('')
    : raw;
  const n = parseInt(full, 16);
  const r = (n >> 16) & 255;
  const g = (n >> 8) & 255;
  const b = n & 255;
  return `rgba(${r}, ${g}, ${b}, ${alpha})`;
}
