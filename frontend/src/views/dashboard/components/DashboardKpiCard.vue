<template>
  <div class="kpi-card">
    <!-- 机甲外边框 -->
    <div class="kpi-border top"></div>
    <div class="kpi-border bottom"></div>
    <div class="kpi-border left"></div>
    <div class="kpi-border right"></div>
    
    <div class="kpi-icon-wrapper">
      <el-icon class="kpi-icon">
        <component :is="iconComponent" />
      </el-icon>
    </div>
    <div class="kpi-info">
      <div class="kpi-label">{{ label }}</div>
      <div class="kpi-value-container">
        <span class="kpi-value">{{ displayValue }}</span>
        <span class="kpi-unit" v-if="unit && displayValue !== '—'">{{ unit }}</span>
      </div>
    </div>
    
    <!-- 数据趋势箭头装饰（示例） -->
    <div class="kpi-trend-decor">
      <div class="trend-dot"></div>
      <div class="trend-line"></div>
    </div>
  </div>
</template>

<script setup>
import { computed, toRefs } from 'vue'
import { User, UserFilled, Postcard, Document, Warning, TrendCharts } from '@element-plus/icons-vue'
import { useNumberAnimation } from '../composables/useNumberAnimation'

const iconMap = { User, UserFilled, Postcard, Document, Warning, TrendCharts }

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

const iconComponent = computed(() => {
  return typeof props.icon === 'string' ? iconMap[props.icon] : props.icon
})
</script>

<style scoped>
.kpi-card {
  position: relative;
  background: var(--cyber-panel-bg);
  padding: 16px 18px;
  display: flex;
  align-items: center;
  gap: 16px;
  overflow: hidden;
  box-shadow:
    inset 0 0 20px rgba(31, 228, 255, 0.05),
    0 0 16px rgba(31, 228, 255, 0.06);
  transition: box-shadow 0.3s ease, background 0.3s ease;
  /* 左下右上的小切角 */
  clip-path: polygon(10px 0, 100% 0, 100% calc(100% - 10px), calc(100% - 10px) 100%, 0 100%, 0 10px);
}

.kpi-card:hover {
  background: var(--cyber-panel-bg-soft);
  box-shadow:
    inset 0 0 28px rgba(31, 228, 255, 0.12),
    0 0 18px rgba(31, 228, 255, 0.12);
}

/* 绘制机甲边框 */
.kpi-border {
  position: absolute;
  background: var(--cyber-border-color);
  z-index: 2;
}

.kpi-border.top { top: 0; left: 10px; right: 0; height: 1px; }
.kpi-border.bottom { bottom: 0; left: 0; right: 10px; height: 1px; }
.kpi-border.left { top: 10px; bottom: 0; left: 0; width: 1px; }
.kpi-border.right { top: 0; bottom: 10px; right: 0; width: 1px; }

/* 顶部高亮科技发光线 */
.kpi-border.top::before {
  content: '';
  position: absolute;
  top: 0;
  left: 10%;
  width: 40%;
  height: 2px;
  background: var(--cyber-accent);
  box-shadow: 0 0 10px var(--cyber-accent-glow);
}

.kpi-icon-wrapper {
  width: 48px;
  height: 48px;
  flex-shrink: 0;
  border-radius: 50%;
  background: linear-gradient(135deg, rgba(31, 228, 255, 0.22) 0%, rgba(47, 123, 255, 0.12) 100%);
  border: 1px solid rgba(31, 228, 255, 0.42);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 0 15px rgba(31, 228, 255, 0.2), inset 0 0 10px rgba(31, 228, 255, 0.2);
}

.kpi-icon {
  font-size: 24px;
  color: var(--cyber-text-primary);
  filter: drop-shadow(0 0 6px var(--cyber-accent-glow));
}

.kpi-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 6px;
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
  color: var(--cyber-text-primary);
  line-height: 1;
  text-shadow: 0 0 10px rgba(244, 251, 255, 0.28), 0 0 18px var(--cyber-accent-glow);
}

.kpi-unit {
  font-size: 14px;
  color: var(--cyber-text-muted);
}

/* 装饰小横条 */
.kpi-trend-decor {
  position: absolute;
  right: 20px;
  bottom: 20px;
  display: flex;
  align-items: center;
  gap: 4px;
  opacity: 0.5;
}

.trend-dot {
  width: 4px;
  height: 4px;
  background: var(--cyber-accent);
  box-shadow: 0 0 4px var(--cyber-accent);
}

.trend-line {
  width: 20px;
  height: 2px;
  background: var(--cyber-accent);
}
</style>
