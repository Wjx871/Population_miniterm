<template>
  <CyberPanel title="业务类型占比">
    <BaseEChart 
      :option="businessTypeShareOption(data)" 
      :empty="!hasData" 
      label="业务类型占比图" 
    />
  </CyberPanel>
</template>

<script setup>
import { computed } from 'vue'
import CyberPanel from './CyberPanel.vue'
import BaseEChart from './BaseEChart.vue'
import { businessTypeShareOption } from '../../../utils/dashboardChart'

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
