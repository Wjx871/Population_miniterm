<template>
  <CyberPanel title="审批状态总览" class="approval-panel">
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

<style scoped>
.approval-panel :deep(.panel-content) {
  min-height: 0;
  overflow: hidden;
}

.approval-panel :deep(.base-echart-wrapper),
.approval-panel :deep(.chart-root) {
  height: 100%;
  min-height: 0;
}
</style>
