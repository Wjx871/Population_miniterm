<template>
  <CyberPanel title="历史规模趋势" class="scale-panel">
    <BaseEChart 
      :option="populationScaleOption(data)" 
      :empty="!hasData" 
      label="人口规模趋势图" 
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
  height: 250px; /* 固定高度，作为中间底部的面板 */
}
</style>
