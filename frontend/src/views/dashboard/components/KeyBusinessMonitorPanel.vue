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
            <span class="monitor-value">{{ item.value !== null ? item.value : '—' }}</span>
            <span class="monitor-unit" v-if="item.value !== null">{{ item.unit }}</span>
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

// 根据文档方案
const monitorList = computed(() => {
  return [
    {
      key: 'keyPopulation',
      label: '重点人口在册',
      value: props.data?.activeKeyPopulation ?? null,
      unit: '人',
      icon: 'UserFilled'
    },
    {
      key: 'cancellation',
      label: '注销申请',
      value: props.data?.pendingCancellation ?? null,
      unit: '件',
      icon: 'CircleClose'
    },
    {
      key: 'expiring',
      label: '即将到期证件',
      value: props.data?.expiringCertificate ?? props.data?.expiringResidencePermits ?? null,
      unit: '张',
      icon: 'Postcard'
    },
    {
      key: 'sensitiveExport',
      label: '敏感导出申请',
      value: props.data?.pendingSensitiveExport ?? null,
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
  background: rgba(10, 30, 70, 0.5);
  border: 1px solid rgba(41, 215, 255, 0.1);
  border-radius: 6px;
  display: flex;
  align-items: center;
  padding: 12px;
  gap: 12px;
  transition: all 0.3s;
}

.monitor-card:hover {
  background: rgba(41, 215, 255, 0.08);
  border-color: rgba(41, 215, 255, 0.3);
  box-shadow: inset 0 0 10px rgba(41, 215, 255, 0.1);
}

.monitor-icon {
  width: 36px;
  height: 36px;
  background: rgba(41, 215, 255, 0.1);
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  color: var(--cyber-accent);
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
  color: #fff;
  font-weight: bold;
  font-family: 'Courier New', Courier, monospace;
}

.monitor-unit {
  font-size: 12px;
  color: var(--cyber-text-muted);
}
</style>
