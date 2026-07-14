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

const render = () => {
  if (!chart || props.empty) return
  chart.setOption(props.option, {
    notMerge: false,
    lazyUpdate: true
  })
}

const resize = () => {
  if (resizeTimer) cancelAnimationFrame(resizeTimer)
  resizeTimer = requestAnimationFrame(() => {
    if (chart) {
      chart.resize()
    }
  })
}

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
  position: relative;
  width: 100%;
  height: 100%;
}

.chart-root {
  width: 100%;
  height: 100%;
  min-height: 220px;
}

.chart-empty {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}
</style>
