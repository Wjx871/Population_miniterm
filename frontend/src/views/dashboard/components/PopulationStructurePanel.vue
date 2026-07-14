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
              <div class="age-bar" :style="{ width: item.percent + '%' }"></div>
            </div>
            <div class="age-value">{{ item.value !== null ? item.percent + '%' : '—' }}</div>
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
  return {
    background: `conic-gradient(var(--cyber-blue) 0% ${maleRatio.value}%, var(--cyber-red) ${maleRatio.value}% 100%)`
  }
})

const ageGroups = computed(() => {
  if (!hasData.value) return []
  const max = Math.max(...props.data.ageGroups.map(g => g.value || 0))
  return props.data.ageGroups.map(g => {
    let percent = 0
    if (g.value !== null && max > 0) {
      percent = Math.round((g.value / max) * 100)
    }
    return {
      label: g.label,
      value: g.value,
      percent: percent || 0
    }
  })
})
</script>

<style scoped>
.structure-container {
  display: flex;
  flex-direction: column;
  height: 100%;
  gap: 20px;
}

.gender-section {
  display: flex;
  align-items: center;
  justify-content: space-around;
  padding: 10px 0;
}

.gender-ring-wrapper {
  position: relative;
  width: 110px;
  height: 110px;
  border-radius: 50%;
  background: radial-gradient(circle at center, rgba(6, 20, 50, 0.9) 0%, rgba(3, 8, 22, 1) 100%);
  padding: 8px;
  box-shadow: 0 0 20px rgba(0, 0, 0, 0.8), inset 0 0 15px rgba(0, 229, 255, 0.2);
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
  background: var(--cyber-red);
  box-shadow: 0 0 8px var(--cyber-red);
}

.legend-item .label {
  color: var(--cyber-text-secondary);
  width: 40px;
}

.legend-item .value {
  color: #fff;
  font-weight: bold;
  font-family: 'Courier New', Courier, monospace;
}

.age-section {
  display: flex;
  flex-direction: column;
  gap: 12px;
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
  color: var(--cyber-text-secondary);
  text-align: right;
}

.age-bar-wrapper {
  flex: 1;
  height: 6px;
  background: rgba(255, 255, 255, 0.05);
  border-radius: 3px;
  overflow: hidden;
  position: relative;
}

/* 背景轨道光环 */
.age-bar-wrapper::before {
  content: '';
  position: absolute;
  top: 0; bottom: 0; left: 0; right: 0;
  box-shadow: inset 0 0 4px rgba(0, 229, 255, 0.1);
  border-radius: 3px;
}

.age-bar {
  height: 100%;
  background: linear-gradient(90deg, rgba(0, 229, 255, 0.2) 0%, #00e5ff 100%);
  border-radius: 3px;
  transition: width 0.6s cubic-bezier(0.4, 0, 0.2, 1);
  box-shadow: 0 0 10px var(--cyber-accent-glow);
  position: relative;
}

/* 光点头部 */
.age-bar::after {
  content: '';
  position: absolute;
  right: 0;
  top: 0;
  height: 100%;
  width: 4px;
  background: #fff;
  border-radius: 2px;
  box-shadow: 0 0 8px #fff;
}

.age-value {
  width: 40px;
  font-size: 13px;
  color: var(--cyber-accent);
  font-family: 'Courier New', Courier, monospace;
}
</style>
