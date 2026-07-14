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
              <div
                class="rank-bar"
                :class="'bar-' + (index + 1)"
                :style="{ width: item.percent + '%' }"
              ></div>
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

  const validData = props.data.filter(
    (item) => (item.value || item.count) !== null && (item.value || item.count) !== undefined
  )
  const sorted = validData
    .sort((a, b) => (b.value || b.count || 0) - (a.value || a.count || 0))
    .slice(0, 5)

  if (sorted.length === 0) return []

  const max = sorted[0].value || sorted[0].count || 0

  return sorted.map((item) => {
    const rawValue = item.value || item.count || 0
    let percent = 0
    if (max > 0) {
      percent = Math.max(6, Math.round((rawValue / max) * 100))
    }

    return {
      label: BUSINESS_LABELS[item.name || item.code] || item.name || item.label,
      value: Number(rawValue).toLocaleString(),
      percent
    }
  })
})
</script>

<style scoped>
.ranking-container {
  display: flex;
  flex-direction: column;
  gap: 14px;
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
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  font-weight: 900;
  color: #7cffff;
  background: rgba(61, 240, 255, 0.12);
  clip-path: polygon(30% 0%, 70% 0%, 100% 30%, 100% 70%, 70% 100%, 30% 100%, 0% 70%, 0% 30%);
  border: 1px solid rgba(61, 240, 255, 0.55);
  box-shadow: inset 0 0 10px rgba(61, 240, 255, 0.35), 0 0 8px rgba(61, 240, 255, 0.2);
  text-shadow: 0 0 6px rgba(61, 240, 255, 0.8);
}

.rank-1 {
  background: rgba(255, 107, 107, 0.22);
  color: #ff8f8f;
  border-color: #ff7b7b;
  box-shadow: inset 0 0 12px rgba(255, 107, 107, 0.5), 0 0 10px rgba(255, 107, 107, 0.25);
  text-shadow: 0 0 8px #ff7b7b;
}

.rank-2 {
  background: rgba(255, 224, 138, 0.2);
  color: #ffe08a;
  border-color: #ffe08a;
  box-shadow: inset 0 0 12px rgba(255, 224, 138, 0.45), 0 0 10px rgba(255, 224, 138, 0.22);
  text-shadow: 0 0 8px #ffe08a;
}

.rank-3 {
  background: rgba(77, 255, 154, 0.18);
  color: #4dff9a;
  border-color: #4dff9a;
  box-shadow: inset 0 0 12px rgba(77, 255, 154, 0.4), 0 0 10px rgba(77, 255, 154, 0.22);
  text-shadow: 0 0 8px #4dff9a;
}

.rank-info {
  flex: 1;
  min-width: 0;
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
  color: #ffffff;
  font-weight: 500;
}

.rank-value {
  font-size: 14px;
  color: #9ef7ff;
  font-family: 'Courier New', Courier, monospace;
  font-weight: 700;
}

.unit {
  font-size: 12px;
  color: #c5e4ff;
  margin-left: 2px;
}

.rank-bar-wrapper {
  height: 10px;
  background: rgba(255, 255, 255, 0.06);
  border-radius: 999px;
  position: relative;
  overflow: hidden;
  box-shadow: inset 0 0 8px rgba(0, 0, 0, 0.25);
}

.rank-bar {
  height: 100%;
  border-radius: 999px;
  position: relative;
  transition: width 0.8s cubic-bezier(0.4, 0, 0.2, 1);
  background: linear-gradient(90deg, rgba(61, 240, 255, 0.25) 0%, #3df0ff 100%);
  box-shadow: 0 0 12px rgba(61, 240, 255, 0.55);
}

.rank-bar::after {
  content: '';
  position: absolute;
  right: 0;
  top: -2px;
  width: 8px;
  height: 14px;
  border-radius: 50%;
  background: #fff;
  box-shadow: 0 0 10px #fff, 0 0 16px currentColor;
}

/* 前几名不同霓虹色 */
.bar-1 {
  background: linear-gradient(90deg, rgba(255, 107, 107, 0.25) 0%, #ff7b7b 100%);
  box-shadow: 0 0 14px rgba(255, 107, 107, 0.55);
  color: #ff7b7b;
}
.bar-2 {
  background: linear-gradient(90deg, rgba(255, 224, 138, 0.25) 0%, #ffe08a 100%);
  box-shadow: 0 0 14px rgba(255, 224, 138, 0.55);
  color: #ffe08a;
}
.bar-3 {
  background: linear-gradient(90deg, rgba(77, 255, 154, 0.25) 0%, #4dff9a 100%);
  box-shadow: 0 0 14px rgba(77, 255, 154, 0.55);
  color: #4dff9a;
}
.bar-4 {
  background: linear-gradient(90deg, rgba(90, 168, 255, 0.25) 0%, #5aa8ff 100%);
  box-shadow: 0 0 14px rgba(90, 168, 255, 0.55);
  color: #5aa8ff;
}
.bar-5 {
  background: linear-gradient(90deg, rgba(192, 140, 255, 0.25) 0%, #c08cff 100%);
  box-shadow: 0 0 14px rgba(192, 140, 255, 0.55);
  color: #c08cff;
}
</style>
