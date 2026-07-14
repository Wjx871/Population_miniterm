<template>
  <CyberPanel title="人口结构分析">
    <template v-if="hasData">
      <div class="structure-container">
        <!-- 性别比例 (CSS conic-gradient) -->
        <div class="gender-section">
          <div class="gender-ring-wrapper">
            <div class="gender-ring" :style="genderRingStyle"></div>
            <div class="gender-center">
              <el-icon><User /></el-icon>
            </div>
          </div>
          <div class="gender-legend">
            <div class="legend-item male">
              <span class="dot"></span>
              <span class="label">男性</span>
              <span class="value">{{ maleRatio }}%</span>
            </div>
            <div class="legend-item female">
              <span class="dot"></span>
              <span class="label">女性</span>
              <span class="value">{{ femaleRatio }}%</span>
            </div>
          </div>
        </div>

        <!-- 年龄分布 (DOM progress bar) -->
        <div class="age-section">
          <div v-for="item in ageGroups" :key="item.label" class="age-row">
            <div class="age-label">{{ item.label }}</div>
            <div class="age-bar-wrapper">
              <div class="age-bar" :style="{ width: item.barPercent + '%' }"></div>
            </div>
            <div class="age-value">{{ item.value !== null ? item.truePercent + '%' : '—' }}</div>
          </div>
        </div>
      </div>
    </template>
    
    <DashboardEmptyState v-else text="暂无人口结构统计" />
  </CyberPanel>
</template>

<script setup>
import { computed } from 'vue'
import { User } from '@element-plus/icons-vue'
import CyberPanel from './CyberPanel.vue'
import DashboardEmptyState from './DashboardEmptyState.vue'

const props = defineProps({
  data: {
    type: Object,
    default: () => null
  }
})

const hasData = computed(() => {
  return props.data && props.data.gender && props.data.ageGroups && props.data.ageGroups.length > 0
})

const maleRatio = computed(() => props.data?.gender?.male || 0)
const femaleRatio = computed(() => props.data?.gender?.female || 0)

const genderRingStyle = computed(() => {
  // 男性亮蓝，女性亮紫 — 提高对比度
  return {
    background: `conic-gradient(#5aa8ff 0% ${maleRatio.value}%, #c08cff ${maleRatio.value}% 100%)`
  }
})

const ageGroups = computed(() => {
  if (!hasData.value) return []
  const max = Math.max(...props.data.ageGroups.map(g => g.value || 0))
  const total = props.data.ageGroups.reduce((sum, g) => sum + (g.value || 0), 0)
  return props.data.ageGroups.map(g => {
    let truePercent = 0
    let barPercent = 0
    if (g.value !== null) {
      if (total > 0) truePercent = Math.round((g.value / total) * 100)
      if (max > 0) barPercent = Math.round((g.value / max) * 100)
    }
    return {
      label: g.label,
      value: g.value,
      truePercent,
      barPercent
    }
  })
})
</script>

<style scoped>
.structure-container {
  display: flex;
  flex-direction: column;
  height: 100%;
  gap: 8px;
}

.gender-section {
  display: flex;
  align-items: center;
  justify-content: space-around;
  padding: 4px 0;
}

.gender-ring-wrapper {
  position: relative;
  width: 90px;
  height: 90px;
  border-radius: 50%;
  background: radial-gradient(circle at center, rgba(12, 40, 80, 0.95) 0%, rgba(6, 24, 52, 1) 100%);
  padding: 8px;
  box-shadow: 0 0 18px rgba(61, 240, 255, 0.25), inset 0 0 15px rgba(61, 240, 255, 0.18);
  display: flex;
  align-items: center;
  justify-content: center;
}

/* 外发光虚线环 */
.gender-ring-wrapper::before {
  content: '';
  position: absolute;
  top: -2px; left: -2px; right: -2px; bottom: -2px;
  border-radius: 50%;
  border: 1px dashed rgba(0, 229, 255, 0.4);
  animation: spin 20s linear infinite;
}

@keyframes spin {
  100% { transform: rotate(360deg); }
}

.gender-ring {
  width: 100%;
  height: 100%;
  border-radius: 50%;
  position: relative;
  z-index: 2;
}

.gender-center {
  position: absolute;
  top: 18px;
  left: 18px;
  right: 18px;
  bottom: 18px;
  background: var(--cyber-bg-color);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28px;
  color: var(--cyber-accent);
  box-shadow: inset 0 0 15px rgba(0, 0, 0, 0.9);
  z-index: 3;
  filter: drop-shadow(0 0 5px var(--cyber-accent-glow));
}

.gender-legend {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
}

.legend-item .dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

.legend-item.male .dot {
  background: var(--cyber-blue);
  box-shadow: 0 0 8px var(--cyber-blue);
}

.legend-item.female .dot {
  background: var(--cyber-purple);
  box-shadow: 0 0 8px var(--cyber-purple);
}

.legend-item .label {
  color: #e2f3ff;
  width: 40px;
}

.legend-item .value {
  color: #ffffff;
  font-weight: bold;
  font-family: 'Courier New', Courier, monospace;
  text-shadow: 0 0 8px rgba(61, 240, 255, 0.35);
}

.age-section {
  display: flex;
  flex-direction: column;
  gap: 8px;
  flex: 1;
  justify-content: center;
}

.age-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.age-label {
  width: 65px;
  font-size: 13px;
  color: #e2f3ff;
  text-align: left;
  flex-shrink: 0;
}

.age-bar-wrapper {
  flex: 1;
  height: 12px;
  background: rgba(255, 255, 255, 0.07);
  border-radius: 999px;
  overflow: hidden;
  position: relative;
  box-shadow: inset 0 0 8px rgba(0, 0, 0, 0.25);
}

.age-bar {
  height: 100%;
  background: linear-gradient(90deg, rgba(90, 168, 255, 0.25) 0%, #3df0ff 55%, #9ef7ff 100%);
  border-radius: 999px;
  transition: width 0.6s cubic-bezier(0.4, 0, 0.2, 1);
  box-shadow: 0 0 14px rgba(61, 240, 255, 0.55);
  position: relative;
}

/* 光点头部 */
.age-bar::after {
  content: '';
  position: absolute;
  right: 0;
  top: -2px;
  height: 16px;
  width: 8px;
  background: #fff;
  border-radius: 50%;
  box-shadow: 0 0 10px #fff, 0 0 16px #3df0ff;
}

.age-value {
  width: 42px;
  font-size: 13px;
  color: #7cffff;
  font-family: 'Courier New', Courier, monospace;
  text-align: right;
  flex-shrink: 0;
  font-weight: 700;
}
</style>
