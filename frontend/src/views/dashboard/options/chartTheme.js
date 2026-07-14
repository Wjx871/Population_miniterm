/** 大屏 ECharts 主题单一真相源 */

export const dashboardChartColors = [
  '#3df0ff', // cyan
  '#4dff9a', // green
  '#ffe08a', // yellow
  '#ff7b7b', // red
  '#5aa8ff', // blue
  '#c08cff'  // purple
];

export const accentCyan = '#3df0ff';
export const accentBlue = '#5aa8ff';
export const accentGreen = '#4dff9a';
export const accentYellow = '#ffe08a';
export const accentRed = '#ff7b7b';
export const chartBg = '#03122b';

const axisLineColor = 'rgba(180, 220, 255, 0.45)';
const splitLineColor = 'rgba(120, 180, 230, 0.18)';
const axisLabelColor = '#d4e9ff';

export const darkTooltip = {
  backgroundColor: 'rgba(4, 22, 48, 0.96)',
  borderColor: 'rgba(77, 240, 255, 0.65)',
  borderWidth: 1,
  textStyle: { color: '#ffffff' },
  extraCssText: 'box-shadow: 0 0 18px rgba(0,136,255,0.22); backdrop-filter: blur(4px);'
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
