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
  position: relative;
  overflow: hidden;
  background:
    radial-gradient(circle at 0% 0%, rgba(61, 240, 255, 0.12) 0%, transparent 45%),
    rgba(12, 48, 96, 0.78);
  border: 1px solid rgba(77, 240, 255, 0.32);
  border-radius: 4px;
  display: flex;
  align-items: center;
  padding: 12px;
  gap: 12px;
  transition: border-color 0.25s, box-shadow 0.25s, background 0.25s;
  box-shadow: inset 0 0 12px rgba(61, 240, 255, 0.08), 0 0 10px rgba(61, 240, 255, 0.08);
}

.monitor-card:nth-child(1) { --m-accent: #3df0ff; }
.monitor-card:nth-child(2) { --m-accent: #ff7b7b; }
.monitor-card:nth-child(3) { --m-accent: #ffe08a; }
.monitor-card:nth-child(4) { --m-accent: #c08cff; }

.monitor-card:hover {
  border-color: color-mix(in srgb, var(--m-accent, #3df0ff) 70%, white 10%);
  box-shadow:
    inset 0 0 14px color-mix(in srgb, var(--m-accent, #3df0ff) 22%, transparent),
    0 0 16px color-mix(in srgb, var(--m-accent, #3df0ff) 28%, transparent);
}

.monitor-icon {
  width: 38px;
  height: 38px;
  flex-shrink: 0;
  background: radial-gradient(circle at 30% 30%, color-mix(in srgb, var(--m-accent, #3df0ff) 35%, transparent), transparent 65%),
    linear-gradient(135deg, color-mix(in srgb, var(--m-accent, #3df0ff) 22%, transparent), rgba(20, 60, 120, 0.3));
  border: 1px solid color-mix(in srgb, var(--m-accent, #3df0ff) 55%, transparent);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  color: var(--m-accent, #3df0ff);
  box-shadow: 0 0 12px color-mix(in srgb, var(--m-accent, #3df0ff) 35%, transparent);
}

.monitor-info {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.monitor-label {
  font-size: 13px;
  color: #e2f3ff;
  font-weight: 500;
}

.monitor-value-wrapper {
  display: flex;
  align-items: baseline;
  gap: 2px;
}

.monitor-value {
  font-size: 22px;
  color: #ffffff;
  font-weight: bold;
  font-family: 'Courier New', Courier, monospace;
  text-shadow: 0 0 10px rgba(61, 240, 255, 0.4);
}

.monitor-unit {
  font-size: 12px;
  color: #c5e4ff;
}
</style>
