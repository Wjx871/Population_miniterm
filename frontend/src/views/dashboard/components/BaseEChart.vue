<!--
  【数据大屏链路 · ECharts 公共容器】
  上游：各 *Panel 传入 option（来自 dashboardChart.js）+ empty/loading
  答辩要点：initEChart(root) 初始化；chart.setOption(option) 是真正画图的一行；卸载时 dispose
-->
<template>
  <div class="base-echart-wrapper">
    <div v-show="!empty" ref="root" class="chart-root" role="img" :aria-label="label"></div>
    <div v-if="empty" class="chart-empty">
      <slot name="empty">
        <DashboardEmptyState />
      </slot>
    </div>
  </div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { initEChart } from '../../../charts/echarts'
import DashboardEmptyState from './DashboardEmptyState.vue'

const props = defineProps({
  option: {
    type: Object,
    required: true
  },
  label: {
    type: String,
    default: '统计图表'
  },
  empty: {
    type: Boolean,
    default: false
  },
  loading: {
    type: Boolean,
    default: false
  }
})

const root = ref(null)
let chart = null
let observer = null
let resizeTimer = null

// 将 option 应用到实例（答辩「真正画图」）
const render = () => {
  if (!chart || props.empty) return
  chart.setOption(props.option, {
    notMerge: false,
    lazyUpdate: true
  })
}

// 容器尺寸变化时 resize，避免大屏 scale/全屏后坐标系错位
const resize = () => {
  if (resizeTimer) cancelAnimationFrame(resizeTimer)
  resizeTimer = requestAnimationFrame(() => {
    if (chart) {
      chart.resize()
    }
  })
}

// 深度监听 option：adapter 刷新数据后自动重绘
watch(() => props.option, render, { deep: true })

watch(() => props.empty, (isEmpty) => {
  if (isEmpty) {
    if (chart) chart.clear()
  } else {
    if (chart) render()
  }
})

watch(() => props.loading, (isLoading) => {
  if (!chart) return
  if (isLoading) {
    chart.showLoading({
      text: '数据加载中',
      color: '#1fe4ff',
      textColor: '#9db8d4',
      maskColor: 'rgba(2, 13, 34, 0.45)',
      zlevel: 0
    })
  } else {
    chart.hideLoading()
  }
})

onMounted(() => {
  // 在 DOM 节点上创建 ECharts 实例（按需注册见 charts/echarts.js）
  chart = initEChart(root.value)
  if (!props.empty) {
    render()
  }

  observer = new ResizeObserver(() => {
    resize()
  })
  if (root.value) {
    observer.observe(root.value)
  }
})

// 销毁：取消动画帧、断开观察、dispose 释放 WebGL/Canvas 资源
onBeforeUnmount(() => {
  if (resizeTimer) cancelAnimationFrame(resizeTimer)
  if (observer) {
    observer.disconnect()
    observer = null
  }
  if (chart) {
    chart.dispose()
    chart = null
  }
})
</script>

<style scoped>
.base-echart-wrapper {
  width: 100%;
  height: 100%;
  min-height: 0;
  position: relative;
}

/* 不设 min-height，避免在固定高度面板内撑破导致坐标轴文字被裁切 */
.chart-root {
  width: 100%;
  height: 100%;
  min-height: 0;
}

.chart-empty {
  width: 100%;
  height: 100%;
  min-height: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}
</style>
