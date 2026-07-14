<template>
  <CyberPanel title="重点业务监控">
    <div class="monitor-grid">
      <div v-for="item in monitorList" :key="item.key" class="monitor-card">
        <div class="monitor-icon">
          <el-icon><component :is="item.icon" /></el-icon>
        </div>
        <div class="monitor-info">
          <div class="monitor-label">{{ item.label }}</div>
          <div class="monitor-value-wrapper">
            <span class="monitor-value">{{ item.displayValue }}</span>
            <span class="monitor-unit">{{ item.unit }}</span>
          </div>
        </div>
      </div>
    </div>
  </CyberPanel>
</template>

<script setup>
import { computed } from 'vue'
import { UserFilled, CircleClose, Postcard, Download } from '@element-plus/icons-vue'
import CyberPanel from './CyberPanel.vue'

const props = defineProps({
  data: {
    type: Object,
    default: () => ({})
  }
})

const formatMonitorValue = (value) => {
  if (value === null || value === undefined || value === '') return '0'
  return value
}

// 根据文档方案
const monitorList = computed(() => {
  return [
    {
      key: 'keyPopulation',
      label: '重点人口在册',
      displayValue: formatMonitorValue(props.data?.activeKeyPopulation),
      unit: '人',
      icon: 'UserFilled'
    },
    {
      key: 'cancellation',
      label: '注销申请',
      displayValue: formatMonitorValue(props.data?.pendingCancellation),
      unit: '件',
      icon: 'CircleClose'
    },
    {
      key: 'expiring',
      label: '即将到期证件',
      displayValue: formatMonitorValue(props.data?.expiringCertificate ?? props.data?.expiringResidencePermits),
      unit: '张',
      icon: 'Postcard'
    },
    {
      key: 'sensitiveExport',
      label: '敏感导出申请',
      displayValue: formatMonitorValue(props.data?.pendingSensitiveExport),
      unit: '件',
      icon: 'Download'
    }
  ]
})
</script>

<script>
export default {
  components: {
    UserFilled, CircleClose, Postcard, Download
  }
}
</script>

<style scoped>
.monitor-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  grid-template-rows: repeat(2, 1fr);
  gap: 12px;
  height: 100%;
}

.monitor-card {
  background: var(--cyber-panel-bg-soft);
  border: 1px solid rgba(31, 228, 255, 0.16);
  border-radius: 4px;
  display: flex;
  align-items: center;
  padding: 12px;
  gap: 12px;
  transition: border-color 0.25s, box-shadow 0.25s, background 0.25s;
  box-shadow: inset 0 0 12px rgba(31, 228, 255, 0.04);
}

.monitor-card:hover {
  background: rgba(31, 228, 255, 0.08);
  border-color: rgba(31, 228, 255, 0.35);
  box-shadow: inset 0 0 12px rgba(31, 228, 255, 0.12), 0 0 12px rgba(31, 228, 255, 0.08);
}

.monitor-icon {
  width: 36px;
  height: 36px;
  flex-shrink: 0;
  background: linear-gradient(135deg, rgba(31, 228, 255, 0.18) 0%, rgba(47, 123, 255, 0.1) 100%);
  border: 1px solid rgba(31, 228, 255, 0.35);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  color: var(--cyber-accent);
  box-shadow: 0 0 10px rgba(31, 228, 255, 0.15);
}

.monitor-info {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.monitor-label {
  font-size: 12px;
  color: var(--cyber-text-secondary);
}

.monitor-value-wrapper {
  display: flex;
  align-items: baseline;
  gap: 2px;
}

.monitor-value {
  font-size: 20px;
  color: var(--cyber-text-primary);
  font-weight: bold;
  font-family: 'Courier New', Courier, monospace;
  text-shadow: 0 0 10px rgba(31, 228, 255, 0.25);
}

.monitor-unit {
  font-size: 12px;
  color: var(--cyber-text-muted);
}
</style>
