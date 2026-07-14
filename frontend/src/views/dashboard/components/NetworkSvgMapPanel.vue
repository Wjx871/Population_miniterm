<template>
  <div class="network-map-panel">
    <BaseEChart 
      :option="networkMapOption" 
      :empty="!hasData" 
      label="中央态势分布图" 
    />
    
    <!-- 装饰光环 -->
    <div class="glow-ring glow-1"></div>
    <div class="glow-ring glow-2"></div>
    <div class="glow-ring glow-3"></div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import BaseEChart from './BaseEChart.vue'
import * as theme from '../options/chartTheme'

const props = defineProps({
  data: {
    type: Array,
    default: () => []
  }
})

const hasData = computed(() => {
  return props.data && props.data.length > 0
})

const networkMapOption = computed(() => {
  if (!hasData.value) return {}

  const nodes = props.data.map((item, index) => {
    // 简单的圆形分布算法分配坐标
    const angle = (index / props.data.length) * Math.PI * 2
    const radius = 60 + Math.random() * 20
    const x = Math.cos(angle) * radius
    const y = Math.sin(angle) * radius
    
    // 模拟不同大小
    const symbolSize = 20 + (item.value / 350000) * 40

    return {
      name: item.regionName,
      value: item.value,
      x,
      y,
      symbolSize: Math.max(15, symbolSize),
      itemStyle: {
        color: index % 2 === 0 ? theme.dashboardChartColors[0] : theme.dashboardChartColors[1],
        shadowBlur: 15,
        shadowColor: index % 2 === 0 ? theme.dashboardChartColors[0] : theme.dashboardChartColors[1]
      },
      label: {
        show: true,
        position: 'right',
        color: '#fff',
        fontSize: 14,
        formatter: '{b}'
      }
    }
  })

  // 中心节点
  nodes.push({
    name: '数据中心',
    value: 0,
    x: 0,
    y: 0,
    symbolSize: 60,
    itemStyle: {
      color: theme.dashboardChartColors[4],
      shadowBlur: 30,
      shadowColor: theme.dashboardChartColors[4]
    },
    label: {
      show: true,
      position: 'bottom',
      color: theme.dashboardChartColors[4],
      fontSize: 16,
      fontWeight: 'bold',
      formatter: '{b}'
    }
  })

  const links = props.data.map((item) => ({
    source: item.regionName,
    target: '数据中心',
    lineStyle: {
      width: 1 + (item.value / 350000) * 3,
      curveness: 0.2
    }
  }))

  return {
    tooltip: { ...theme.darkTooltip },
    series: [
      {
        type: 'graph',
        layout: 'none',
        coordinateSystem: null,
        data: nodes,
        links: links,
        roam: false,
        lineStyle: {
          color: 'source',
          opacity: 0.4
        },
        edgeSymbol: ['none', 'arrow'],
        edgeSymbolSize: 8,
        labelLayout: {
          hideOverlap: false
        }
      },
      {
        type: 'effectScatter',
        coordinateSystem: null,
        data: nodes.map(n => ({ ...n, symbolSize: n.symbolSize * 0.8 })),
        showEffectOn: 'render',
        rippleEffect: {
          brushType: 'stroke',
          scale: 2.5
        },
        hoverAnimation: true,
        zlevel: 1
      }
    ]
  }
})
</script>

<style scoped>
.network-map-panel {
  position: relative;
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}

/* 底部发光光环 */
.glow-ring {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  border-radius: 50%;
  border: 1px solid rgba(41, 215, 255, 0.1);
  pointer-events: none;
  z-index: 0;
}

.glow-1 {
  width: 300px;
  height: 300px;
  background: radial-gradient(circle, rgba(41, 215, 255, 0.05) 0%, transparent 70%);
  animation: pulse 4s infinite alternate;
}

.glow-2 {
  width: 450px;
  height: 450px;
  border-style: dashed;
  opacity: 0.5;
  animation: spin 30s linear infinite;
}

.glow-3 {
  width: 600px;
  height: 600px;
  border-color: rgba(59, 130, 246, 0.2);
  animation: spin-reverse 40s linear infinite;
}

@keyframes pulse {
  0% { transform: translate(-50%, -50%) scale(0.95); opacity: 0.8; }
  100% { transform: translate(-50%, -50%) scale(1.05); opacity: 0.4; }
}

@keyframes spin {
  from { transform: translate(-50%, -50%) rotate(0deg); }
  to { transform: translate(-50%, -50%) rotate(360deg); }
}

@keyframes spin-reverse {
  from { transform: translate(-50%, -50%) rotate(360deg); }
  to { transform: translate(-50%, -50%) rotate(0deg); }
}
</style>
