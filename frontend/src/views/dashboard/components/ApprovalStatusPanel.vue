<template>
  <CyberPanel title="审批状态总览">
    <BaseEChart 
      :option="approvalStatusOption(data)" 
      :empty="!hasData" 
      label="审批状态环形图" 
    />
  </CyberPanel>
</template>

<script setup>
import { computed } from 'vue'
import CyberPanel from './CyberPanel.vue'
import BaseEChart from './BaseEChart.vue'
import { approvalStatusOption } from '../../../utils/dashboardChart'

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
