<!--
  【数据大屏 · 迁入迁出趋势】
  数据：charts.migrationTrend（useDashboardData → normalizeDashboardCharts）
  绘制：migrationTrendOption → BaseEChart.setOption（双折线）
-->
<template>
  <CyberPanel title="近30日迁入迁出趋势">
    <BaseEChart
      :option="migrationTrendOption(data)"
      :empty="!hasData"
      label="迁入迁出趋势图"
    />
  </CyberPanel>
</template>

<script setup>
import { computed } from 'vue'
import CyberPanel from './CyberPanel.vue'
import BaseEChart from './BaseEChart.vue'
import { migrationTrendOption } from '../../../utils/dashboardChart'

const props = defineProps({
  data: {
    type: Array,
    default: () => []
  }
})

// 有序列点才画图，否则 BaseEChart 展示空态
const hasData = computed(() => {
  return props.data && props.data.length > 0
})
</script>
