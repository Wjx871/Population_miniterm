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
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 15px;
  font-weight: 900;
  color: var(--cyber-accent);
  background: rgba(0, 229, 255, 0.1);
  /* 赛博切角六边形 */
  clip-path: polygon(30% 0%, 70% 0%, 100% 30%, 100% 70%, 70% 100%, 30% 100%, 0% 70%, 0% 30%);
  border: 1px solid rgba(0, 229, 255, 0.5);
  box-shadow: inset 0 0 8px rgba(0, 229, 255, 0.4);
  text-shadow: 0 0 5px var(--cyber-accent);
}

/* 前三名特殊高亮着色 */
.rank-1 {
  background: rgba(255, 69, 0, 0.2);
  color: #ff4500;
  text-shadow: 0 0 8px #ff4500;
  border-color: #ff4500;
  box-shadow: inset 0 0 10px rgba(255, 69, 0, 0.6);
}

.rank-2 {
  background: rgba(255, 165, 0, 0.2);
  color: #ffa500;
  text-shadow: 0 0 8px #ffa500;
  border-color: #ffa500;
  box-shadow: inset 0 0 10px rgba(255, 165, 0, 0.6);
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
  background: repeating-linear-gradient(
    90deg,
    rgba(255, 255, 255, 0.02),
    rgba(255, 255, 255, 0.02) 4px,
    transparent 4px,
    transparent 6px
  );
  border-radius: 0;
  position: relative;
  border-bottom: 1px solid rgba(255, 255, 255, 0.05);
}

.rank-bar {
  height: 100%;
  background: linear-gradient(90deg, rgba(0, 229, 255, 0.1) 0%, rgba(0, 229, 255, 1) 100%);
  transition: width 0.8s cubic-bezier(0.4, 0, 0.2, 1);
  box-shadow: 0 0 10px var(--cyber-accent-glow);
  position: relative;
}

/* 分段式能量罩特效 (遮罩法) */
.rank-bar::before {
  content: '';
  position: absolute;
  top: 0; left: 0; right: 0; bottom: 0;
  background: repeating-linear-gradient(
    90deg,
    transparent,
    transparent 4px,
    var(--cyber-panel-bg) 4px,
    var(--cyber-panel-bg) 6px
  );
}

/* 高亮头 */
.rank-bar::after {
  content: '';
  position: absolute;
  right: 0;
  top: -2px;
  height: 10px;
  width: 2px;
  background: #fff;
  box-shadow: 0 0 8px #fff, 0 0 15px var(--cyber-accent);
}
</style>
