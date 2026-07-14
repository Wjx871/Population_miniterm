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
  width: 100px;
  height: 100px;
  border-radius: 50%;
  background: var(--cyber-panel-bg);
  padding: 8px;
  box-shadow: 0 0 15px rgba(0, 0, 0, 0.5);
}

.gender-ring {
  width: 100%;
  height: 100%;
  border-radius: 50%;
}

.gender-center {
  position: absolute;
  top: 14px;
  left: 14px;
  right: 14px;
  bottom: 14px;
  background: var(--cyber-bg-color);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  color: var(--cyber-text-secondary);
  box-shadow: inset 0 0 10px rgba(0,0,0,0.8);
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
  height: 8px;
  background: rgba(41, 215, 255, 0.1);
  border-radius: 4px;
  overflow: hidden;
}

.age-bar {
  height: 100%;
  background: linear-gradient(90deg, var(--cyber-blue) 0%, var(--cyber-accent) 100%);
  border-radius: 4px;
  transition: width 0.6s cubic-bezier(0.4, 0, 0.2, 1);
  box-shadow: 0 0 8px var(--cyber-accent-glow);
}

.age-value {
  width: 40px;
  font-size: 13px;
  color: var(--cyber-accent);
  font-family: 'Courier New', Courier, monospace;
}
</style>
