<template>
  <CyberPanel title="业务办理量排行">
    <template v-if="hasData">
      <div class="ranking-container">
        <div v-for="(item, index) in rankingList" :key="item.label" class="ranking-item">
          <div class="rank-number" :class="'rank-' + (index + 1)">{{ index + 1 }}</div>
          <div class="rank-info">
            <div class="rank-header">
              <span class="rank-label">{{ item.label }}</span>
              <span class="rank-value">{{ item.value }}<span class="unit">件</span></span>
            </div>
            <div class="rank-bar-wrapper">
              <div class="rank-bar" :style="{ width: item.percent + '%' }"></div>
            </div>
          </div>
        </div>
      </div>
    </template>
    <DashboardEmptyState v-else text="暂无业务排行数据" />
  </CyberPanel>
</template>

<script setup>
import { computed } from 'vue'
import CyberPanel from './CyberPanel.vue'
import DashboardEmptyState from './DashboardEmptyState.vue'

const props = defineProps({
  data: {
    type: Array,
    default: () => []
  }
})

const BUSINESS_LABELS = {
  MIGRATION_IN: '迁入登记',
  MIGRATION_OUT: '迁出登记',
  RESIDENCE_PERMIT: '居住证业务',
  CANCELLATION: '注销申请',
  KEY_POPULATION: '重点人口建档',
  CERTIFICATE: '证件管理',
  FLOATING_POPULATION: '流动人口登记'
}

const hasData = computed(() => {
  return props.data && props.data.length > 0
})

const rankingList = computed(() => {
  if (!hasData.value) return []
  
  // 过滤出有值的数据，并排序，取前 5
  const validData = props.data.filter(item => (item.value || item.count) !== null && (item.value || item.count) !== undefined)
  const sorted = validData.sort((a, b) => (b.value || b.count || 0) - (a.value || a.count || 0)).slice(0, 5)
  
  if (sorted.length === 0) return []
  
  const max = sorted[0].value || sorted[0].count || 0
  
  return sorted.map(item => {
    const rawValue = item.value || item.count || 0
    let percent = 0
    if (max > 0) {
      percent = Math.round((rawValue / max) * 100)
    }
    
    return {
      label: BUSINESS_LABELS[item.name || item.code] || item.name || item.label,
      value: rawValue,
      percent
    }
  })
})
</script>

<style scoped>
.ranking-container {
  display: flex;
  flex-direction: column;
  gap: 16px;
  height: 100%;
  justify-content: center;
}

.ranking-item {
  display: flex;
  align-items: center;
  gap: 12px;
}

.rank-number {
  width: 24px;
  height: 24px;
  border-radius: 4px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  font-weight: bold;
  background: rgba(41, 215, 255, 0.1);
  color: var(--cyber-text-secondary);
  border: 1px solid rgba(41, 215, 255, 0.3);
}

.rank-1 {
  background: rgba(255, 69, 0, 0.2);
  color: #ff4500;
  border-color: #ff4500;
}

.rank-2 {
  background: rgba(255, 165, 0, 0.2);
  color: #ffa500;
  border-color: #ffa500;
}

.rank-3 {
  background: rgba(252, 211, 77, 0.2);
  color: var(--cyber-yellow);
  border-color: var(--cyber-yellow);
}

.rank-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.rank-header {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
}

.rank-label {
  font-size: 13px;
  color: var(--cyber-text-primary);
}

.rank-value {
  font-size: 14px;
  color: var(--cyber-accent);
  font-family: 'Courier New', Courier, monospace;
}

.unit {
  font-size: 12px;
  color: var(--cyber-text-muted);
  margin-left: 2px;
}

.rank-bar-wrapper {
  height: 6px;
  background: rgba(41, 215, 255, 0.1);
  border-radius: 3px;
  overflow: hidden;
}

.rank-bar {
  height: 100%;
  background: linear-gradient(90deg, var(--cyber-accent) 0%, var(--cyber-green) 100%);
  border-radius: 3px;
  transition: width 0.8s cubic-bezier(0.4, 0, 0.2, 1);
  box-shadow: 0 0 8px var(--cyber-accent-glow);
}
</style>
