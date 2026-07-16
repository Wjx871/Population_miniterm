<template>
  <CyberPanel title="重点业务监控">
    <div v-if="hasData" class="monitor-grid">
      <div v-for="item in monitorList" :key="item.key" class="monitor-card">
        <div class="monitor-icon"><el-icon><component :is="item.icon" /></el-icon></div>
        <div class="monitor-info">
          <div class="monitor-label">{{ item.label }}</div>
          <div class="monitor-value-wrapper">
            <span class="monitor-value">{{ item.value.toLocaleString() }}</span>
            <span class="monitor-unit">{{ item.unit }}</span>
          </div>
        </div>
      </div>
    </div>
    <DashboardEmptyState v-else text="暂无重点业务统计" />
  </CyberPanel>
</template>

<script setup>
import { computed } from 'vue'
import { UserFilled, CircleClose, Postcard, Download } from '@element-plus/icons-vue'
import CyberPanel from './CyberPanel.vue'
import DashboardEmptyState from './DashboardEmptyState.vue'

const props = defineProps({
  data: {
    type: Object,
    default: null
  }
})

const requiredFields = [
  'activeKeyPopulation',
  'pendingCancellation',
  'expiringResidencePermits',
  'pendingSensitiveExport'
]

const hasData = computed(() => props.data && requiredFields.every((key) => Number.isFinite(props.data[key])))

const monitorList = computed(() => [
  { key: 'keyPopulation', label: '重点人口在册', value: props.data.activeKeyPopulation, unit: '人', icon: UserFilled },
  { key: 'cancellation', label: '注销申请', value: props.data.pendingCancellation, unit: '件', icon: CircleClose },
  { key: 'expiring', label: '即将到期证件', value: props.data.expiringResidencePermits, unit: '张', icon: Postcard },
  { key: 'sensitiveExport', label: '敏感导出申请', value: props.data.pendingSensitiveExport, unit: '件', icon: Download }
])
</script>

<style scoped>
.monitor-grid { display: grid; grid-template-columns: repeat(2, 1fr); grid-template-rows: repeat(2, 1fr); gap: 12px; height: 100%; }
.monitor-card { position: relative; overflow: hidden; background: radial-gradient(circle at 0% 0%, rgba(61, 240, 255, 0.12) 0%, transparent 45%), rgba(12, 48, 96, 0.78); border: 1px solid rgba(77, 240, 255, 0.32); border-radius: 4px; display: flex; align-items: center; padding: 12px; gap: 12px; box-shadow: inset 0 0 12px rgba(61, 240, 255, 0.08), 0 0 10px rgba(61, 240, 255, 0.08); }
.monitor-card:nth-child(1) { --m-accent: #3df0ff; }.monitor-card:nth-child(2) { --m-accent: #ff7b7b; }.monitor-card:nth-child(3) { --m-accent: #ffe08a; }.monitor-card:nth-child(4) { --m-accent: #c08cff; }
.monitor-icon { width: 38px; height: 38px; flex-shrink: 0; background: linear-gradient(135deg, color-mix(in srgb, var(--m-accent) 22%, transparent), rgba(20, 60, 120, 0.3)); border: 1px solid color-mix(in srgb, var(--m-accent) 55%, transparent); border-radius: 50%; display: flex; align-items: center; justify-content: center; font-size: 18px; color: var(--m-accent); box-shadow: 0 0 12px color-mix(in srgb, var(--m-accent) 35%, transparent); }
.monitor-info { flex: 1; display: flex; flex-direction: column; }.monitor-label { font-size: 13px; color: #e2f3ff; font-weight: 500; }.monitor-value-wrapper { display: flex; align-items: baseline; gap: 2px; }.monitor-value { font-size: 22px; color: #fff; font-weight: bold; font-family: 'Courier New', Courier, monospace; text-shadow: 0 0 10px rgba(61, 240, 255, 0.4); }.monitor-unit { font-size: 12px; color: #c5e4ff; }
</style>
