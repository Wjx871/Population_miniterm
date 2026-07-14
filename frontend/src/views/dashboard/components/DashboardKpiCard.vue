<template>
  <div class="kpi-card">
    <div class="kpi-icon-wrapper">
      <el-icon class="kpi-icon">
        <component :is="icon" />
      </el-icon>
    </div>
    <div class="kpi-info">
      <div class="kpi-label">{{ label }}</div>
      <div class="kpi-value-container">
        <span class="kpi-value">{{ displayValue }}</span>
        <span class="kpi-unit" v-if="unit && displayValue !== '—'">{{ unit }}</span>
      </div>
    </div>
    <!-- 装饰角 -->
    <div class="kpi-corner kpi-top-left"></div>
    <div class="kpi-corner kpi-bottom-right"></div>
  </div>
</template>

<script setup>
import { computed, toRefs } from 'vue'
import { useNumberAnimation } from '../composables/useNumberAnimation'

const props = defineProps({
  label: String,
  value: {
    type: [Number, String],
    default: null
  },
  unit: {
    type: String,
    default: '人'
  },
  icon: {
    type: String,
    default: 'User'
  }
})

const { value } = toRefs(props)
const animatedValue = useNumberAnimation(value)

const displayValue = computed(() => {
  if (props.value === null || props.value === undefined || props.value === '') {
    return '—'
  }
  return animatedValue.value.toLocaleString()
})
</script>

<style scoped>
.kpi-card {
  position: relative;
  background: linear-gradient(135deg, rgba(10, 30, 70, 0.6) 0%, rgba(2, 15, 40, 0.8) 100%);
  border: 1px solid rgba(41, 215, 255, 0.15);
  border-radius: 8px;
  padding: 16px;
  display: flex;
  align-items: center;
  gap: 16px;
  overflow: hidden;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.3);
  transition: all 0.3s ease;
}

.kpi-card:hover {
  border-color: rgba(41, 215, 255, 0.4);
  box-shadow: 0 0 16px rgba(41, 215, 255, 0.15);
  transform: translateY(-2px);
}

/* 内部发光 */
.kpi-card::before {
  content: '';
  position: absolute;
  top: 0; left: 0; right: 0; bottom: 0;
  background: radial-gradient(circle at 10% 50%, rgba(41, 215, 255, 0.05) 0%, transparent 60%);
  pointer-events: none;
}

.kpi-icon-wrapper {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  background: rgba(41, 215, 255, 0.1);
  border: 1px solid rgba(41, 215, 255, 0.3);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: inset 0 0 10px rgba(41, 215, 255, 0.2);
}

.kpi-icon {
  font-size: 24px;
  color: var(--cyber-accent);
  filter: drop-shadow(0 0 4px var(--cyber-accent-glow));
}

.kpi-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 4px;
}

.kpi-label {
  font-size: 14px;
  color: var(--cyber-text-secondary);
  letter-spacing: 1px;
}

.kpi-value-container {
  display: flex;
  align-items: baseline;
  gap: 4px;
}

.kpi-value {
  font-family: 'Courier New', Courier, monospace;
  font-size: 28px;
  font-weight: bold;
  color: #fff;
  line-height: 1;
  text-shadow: 0 0 8px rgba(255, 255, 255, 0.4);
}

.kpi-unit {
  font-size: 12px;
  color: var(--cyber-text-muted);
}

/* 装饰角 */
.kpi-corner {
  position: absolute;
  width: 8px;
  height: 8px;
  border: 2px solid var(--cyber-accent);
  opacity: 0.6;
}

.kpi-top-left {
  top: 0;
  left: 0;
  border-right: none;
  border-bottom: none;
  border-top-left-radius: 8px;
}

.kpi-bottom-right {
  bottom: 0;
  right: 0;
  border-left: none;
  border-top: none;
  border-bottom-right-radius: 8px;
}
</style>
