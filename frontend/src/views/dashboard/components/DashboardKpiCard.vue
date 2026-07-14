<template>
  <div class="kpi-card" :class="'tone-' + tone">
    <div class="kpi-border top"></div>
    <div class="kpi-border bottom"></div>
    <div class="kpi-border left"></div>
    <div class="kpi-border right"></div>
    <div class="kpi-scan" aria-hidden="true"></div>

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
  },
  /** 卡片主色调：cyan / green / yellow / blue / purple / red */
  tone: {
    type: String,
    default: 'cyan'
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
  --kpi-accent: #3df0ff;
  --kpi-accent-soft: rgba(61, 240, 255, 0.22);
  --kpi-glow: rgba(61, 240, 255, 0.45);

  position: relative;
  background:
    radial-gradient(circle at 12% 20%, var(--kpi-accent-soft) 0%, transparent 42%),
    linear-gradient(135deg, rgba(14, 52, 108, 0.96) 0%, rgba(8, 34, 74, 0.98) 100%);
  padding: 16px 18px;
  display: flex;
  align-items: center;
  gap: 16px;
  overflow: hidden;
  border: 1px solid color-mix(in srgb, var(--kpi-accent) 45%, transparent);
  box-shadow:
    inset 0 0 18px color-mix(in srgb, var(--kpi-accent) 16%, transparent),
    0 0 16px color-mix(in srgb, var(--kpi-accent) 18%, transparent);
  transition: box-shadow 0.3s ease, border-color 0.3s ease, transform 0.3s ease;
  clip-path: polygon(10px 0, 100% 0, 100% calc(100% - 10px), calc(100% - 10px) 100%, 0 100%, 0 10px);
}

.kpi-card.tone-cyan { --kpi-accent: #3df0ff; --kpi-accent-soft: rgba(61, 240, 255, 0.22); }
.kpi-card.tone-green { --kpi-accent: #4dff9a; --kpi-accent-soft: rgba(77, 255, 154, 0.2); }
.kpi-card.tone-yellow { --kpi-accent: #ffe08a; --kpi-accent-soft: rgba(255, 224, 138, 0.18); }
.kpi-card.tone-blue { --kpi-accent: #5aa8ff; --kpi-accent-soft: rgba(90, 168, 255, 0.2); }
.kpi-card.tone-purple { --kpi-accent: #c08cff; --kpi-accent-soft: rgba(192, 140, 255, 0.2); }
.kpi-card.tone-red { --kpi-accent: #ff7b7b; --kpi-accent-soft: rgba(255, 123, 123, 0.18); }

.kpi-card:hover {
  border-color: color-mix(in srgb, var(--kpi-accent) 75%, white 10%);
  box-shadow:
    inset 0 0 26px color-mix(in srgb, var(--kpi-accent) 22%, transparent),
    0 0 22px color-mix(in srgb, var(--kpi-accent) 28%, transparent);
}

.kpi-scan {
  position: absolute;
  inset: 0;
  background: linear-gradient(
    110deg,
    transparent 30%,
    color-mix(in srgb, var(--kpi-accent) 18%, transparent) 48%,
    transparent 66%
  );
  transform: translateX(-120%);
  animation: kpi-sweep 4.8s ease-in-out infinite;
  pointer-events: none;
  z-index: 1;
}

@keyframes kpi-sweep {
  0%, 55% { transform: translateX(-120%); opacity: 0; }
  60% { opacity: 1; }
  85% { transform: translateX(120%); opacity: 0.7; }
  100% { transform: translateX(120%); opacity: 0; }
}

.kpi-border {
  position: absolute;
  background: color-mix(in srgb, var(--kpi-accent) 55%, transparent);
  z-index: 2;
}

.kpi-border.top { top: 0; left: 10px; right: 0; height: 1px; }
.kpi-border.bottom { bottom: 0; left: 0; right: 10px; height: 1px; }
.kpi-border.left { top: 10px; bottom: 0; left: 0; width: 1px; }
.kpi-border.right { top: 0; bottom: 10px; right: 0; width: 1px; }

.kpi-border.top::before {
  content: '';
  position: absolute;
  top: 0;
  left: 8%;
  width: 42%;
  height: 2px;
  background: var(--kpi-accent);
  box-shadow: 0 0 12px var(--kpi-accent);
  animation: top-glow 2.8s ease-in-out infinite;
}

@keyframes top-glow {
  0%, 100% { opacity: 0.55; width: 34%; }
  50% { opacity: 1; width: 48%; }
}

.kpi-icon-wrapper {
  width: 48px;
  height: 48px;
  flex-shrink: 0;
  border-radius: 50%;
  background: radial-gradient(circle at 30% 30%, color-mix(in srgb, var(--kpi-accent) 35%, transparent), transparent 60%),
    linear-gradient(135deg, color-mix(in srgb, var(--kpi-accent) 28%, transparent), rgba(20, 60, 120, 0.35));
  border: 1px solid color-mix(in srgb, var(--kpi-accent) 55%, transparent);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow:
    0 0 16px color-mix(in srgb, var(--kpi-accent) 35%, transparent),
    inset 0 0 12px color-mix(in srgb, var(--kpi-accent) 25%, transparent);
  z-index: 2;
}

.kpi-icon {
  font-size: 24px;
  color: #ffffff;
  filter: drop-shadow(0 0 8px var(--kpi-accent));
}

.kpi-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 6px;
  z-index: 2;
}

.kpi-label {
  font-size: 14px;
  color: #e8f6ff;
  letter-spacing: 1px;
  font-weight: 500;
}

.kpi-value-container {
  display: flex;
  align-items: baseline;
  gap: 4px;
}

.kpi-value {
  font-family: 'Courier New', Courier, monospace;
  font-size: 30px;
  font-weight: bold;
  color: #ffffff;
  line-height: 1;
  text-shadow:
    0 0 10px rgba(255, 255, 255, 0.35),
    0 0 18px var(--kpi-accent);
}

.kpi-unit {
  font-size: 14px;
  color: #c5e4ff;
}

.kpi-trend-decor {
  position: absolute;
  right: 18px;
  bottom: 16px;
  display: flex;
  align-items: center;
  gap: 4px;
  opacity: 0.75;
  z-index: 2;
}

.trend-dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--kpi-accent);
  box-shadow: 0 0 8px var(--kpi-accent);
  animation: pulse-dot 1.8s ease-in-out infinite;
}

.trend-line {
  width: 22px;
  height: 2px;
  background: linear-gradient(90deg, var(--kpi-accent), transparent);
}

@keyframes pulse-dot {
  0%, 100% { transform: scale(1); opacity: 0.7; }
  50% { transform: scale(1.35); opacity: 1; }
}
</style>
