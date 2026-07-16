<template>
  <CyberPanel title="审批状态总览" class="approval-panel">
    <template v-if="hasData">
      <div class="approval-layout">
        <div class="approval-chart">
          <BaseEChart
            :option="chartOption"
            :empty="false"
            label="审批状态环形图"
          />
        </div>
        <ul class="approval-legend">
          <li v-for="item in legendItems" :key="item.name" class="legend-row">
            <span class="legend-dot" :style="{ background: item.color, boxShadow: `0 0 8px ${item.color}` }"></span>
            <span class="legend-name">{{ item.name }}</span>
            <span class="legend-value">{{ item.valueText }}</span>
            <span class="legend-percent">{{ item.percentText }}</span>
          </li>
        </ul>
      </div>
    </template>
    <DashboardEmptyState v-else text="暂无审批状态数据" />
  </CyberPanel>
</template>

<script setup>
import { computed } from 'vue'
import CyberPanel from './CyberPanel.vue'
import BaseEChart from './BaseEChart.vue'
import DashboardEmptyState from './DashboardEmptyState.vue'
import { approvalStatusOption } from '../../../utils/dashboardChart'

const props = defineProps({
  data: {
    type: Array,
    default: () => []
  }
})

const colorMap = {
  PENDING: '#ffe08a',
  APPROVED: '#4dff9a',
  REJECTED: '#ff7b7b',
  COMPLETED: '#5aa8ff'
}

const labelMap = {
  PENDING: '待审批',
  APPROVED: '已通过',
  REJECTED: '已驳回',
  COMPLETED: '已办结'
}

// 只要是数组就视为"有数据"。0 长度也表示后端正常返回了（哪怕只有 APPROVED 一项）
const hasData = computed(() => Array.isArray(props.data))

const normalizedRows = computed(() => {
  if (!hasData.value) return []
  return props.data.map((item) => {
    const code = item.name || item.code || item.label || '未知'
    return {
      code,
      name: labelMap[code] || code,
      value: Number(item.count || item.value || 0),
      color: colorMap[code] || '#3df0ff'
    }
  })
})

const total = computed(() =>
  normalizedRows.value.reduce((sum, item) => sum + item.value, 0)
)

const legendItems = computed(() => {
  return normalizedRows.value.map((item) => {
    const percent = total.value > 0 ? Math.round((item.value / total.value) * 100) : 0
    return {
      name: item.name,
      color: item.color,
      valueText: item.value.toLocaleString(),
      percentText: `${percent}%`
    }
  })
})

// 图表只画环 + 中心总计，图例改由 DOM 渲染，避免重叠
const chartOption = computed(() => {
  const option = approvalStatusOption(props.data || [])
  return {
    ...option,
    legend: { show: false },
    tooltip: {
      ...option.tooltip,
      confine: true,
      position: (point, _params, _dom, _rect, size) => {
        // 尽量把 tooltip 放在环图左侧空白，避免盖住右侧图例
        const [x, y] = point
        const boxW = size.contentSize[0]
        const boxH = size.contentSize[1]
        const viewW = size.viewSize[0]
        const viewH = size.viewSize[1]
        let left = x - boxW - 12
        let top = y - boxH / 2
        if (left < 8) left = Math.min(x + 12, viewW - boxW - 8)
        if (top < 8) top = 8
        if (top + boxH > viewH - 8) top = viewH - boxH - 8
        return [left, top]
      }
    },
    title: {
      ...option.title,
      left: '50%',
      top: '50%'
    },
    series: (option.series || []).map((s) => ({
      ...s,
      center: ['50%', '50%'],
      radius: ['54%', '74%']
    }))
  }
})
</script>

<style scoped>
.approval-panel :deep(.panel-content) {
  min-height: 0;
  overflow: hidden;
}

.approval-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.05fr) minmax(150px, 0.95fr);
  gap: 8px 12px;
  height: 100%;
  min-height: 0;
  align-items: center;
}

.approval-chart {
  height: 100%;
  min-height: 0;
  min-width: 0;
}

.approval-chart :deep(.base-echart-wrapper),
.approval-chart :deep(.chart-root) {
  height: 100%;
  min-height: 0;
}

.approval-legend {
  list-style: none;
  margin: 0;
  padding: 4px 2px 4px 0;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 12px;
  min-width: 0;
}

.legend-row {
  display: grid;
  grid-template-columns: 10px minmax(48px, 1fr) auto auto;
  align-items: center;
  column-gap: 8px;
  min-width: 0;
}

.legend-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}

.legend-name {
  color: #e8f6ff;
  font-size: 13px;
  white-space: nowrap;
}

.legend-value {
  color: #ffffff;
  font-size: 13px;
  font-weight: 700;
  font-family: 'Courier New', Courier, monospace;
  text-align: right;
  white-space: nowrap;
  min-width: 4.5em;
}

.legend-percent {
  color: #9ad8ff;
  font-size: 12px;
  text-align: right;
  white-space: nowrap;
  min-width: 2.6em;
}
</style>
