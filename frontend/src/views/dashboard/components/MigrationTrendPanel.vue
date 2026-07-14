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

const hasData = computed(() => {
  return props.data && props.data.length > 0
})
</script>
