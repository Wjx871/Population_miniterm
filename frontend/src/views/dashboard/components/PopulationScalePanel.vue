<template>
  <CyberPanel title="近 30 日登记与签发趋势" class="scale-panel">
    <BaseEChart 
      :option="populationScaleOption(data)" 
      :empty="!hasData" 
      label="近 30 日登记与签发趋势图"
    />
  </CyberPanel>
</template>

<script setup>
import { computed } from 'vue'
import CyberPanel from './CyberPanel.vue'
import BaseEChart from './BaseEChart.vue'
import { populationScaleOption } from '../../../utils/dashboardChart'

const props = defineProps({
  data: {
    type: Array,
    default: () => []
  }
})

const hasData = computed(() => {
  return props.data && props.data.length > 0
})
</script>

<style scoped>
.scale-panel {
  height: 100%;
  min-height: 0;
}

/* 确保图表区域吃满面板内容区，不溢出裁切坐标轴 */
.scale-panel :deep(.panel-content) {
  min-height: 0;
  overflow: hidden;
}

.scale-panel :deep(.base-echart-wrapper),
.scale-panel :deep(.chart-root) {
  height: 100%;
  min-height: 0;
}
</style>
