<!--
  【数据大屏 · 业务申请类型占比】
  数据：charts.businessScale（与排行榜同源，展示形态不同）
  绘制：businessTypeShareOption → 横向柱状图 BaseEChart
-->
<template>
  <CyberPanel title="业务申请类型占比">
    <BaseEChart
      :option="businessTypeShareOption(data)"
      :empty="!hasData"
      label="业务申请类型占比图"
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
