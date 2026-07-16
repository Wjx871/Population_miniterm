<template>
  <footer class="dashboard-footer">
    <div class="footer-left">
      <span class="label">数据来源：</span>
      <span class="value">{{ isDemo ? '内置演示数据' : '人口数据库管理系统' }}</span>
    </div>
    <div class="footer-center">
      <span class="label">数据更新时间：</span>
      <span class="value">{{ formattedTime }}</span>
    </div>
    <div class="footer-right">
      <span class="label">系统运行状态：</span>
      <span class="status-indicator" :class="statusClass">
        <span class="dot"></span>
        {{ statusText }}
      </span>
    </div>
  </footer>
</template>

<script setup>
import { computed } from 'vue'
import { formatDateTime } from '../../../utils/date'

const props = defineProps({
  updateTime: String,
  isDemo: Boolean,
  overviewError: Boolean,
  chartsError: Boolean
})

const formattedTime = computed(() => {
  if (!props.updateTime) return '未加载'
  const formatted = formatDateTime(props.updateTime)
  return formatted || props.updateTime
})

const statusClass = computed(() => {
  if (props.overviewError && props.chartsError) return 'error'
  if (props.overviewError || props.chartsError) return 'warning'
  return 'success'
})

const statusText = computed(() => {
  if (props.overviewError && props.chartsError) return '数据加载失败'
  if (props.overviewError || props.chartsError) return '部分数据异常'
  return '正常运行'
})
</script>

<style scoped>
.dashboard-footer {
  height: 40px;
  flex-shrink: 0;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 40px;
  font-size: 13px;
  color: #d8ecff;
  border-top: 1px solid rgba(77, 240, 255, 0.28);
  background: linear-gradient(0deg, rgba(4, 22, 48, 0.96) 0%, rgba(8, 36, 72, 0.72) 100%);
  box-shadow: inset 0 1px 0 rgba(77, 240, 255, 0.18);
}

.label {
  color: #b7d8f5;
}

.value {
  color: #ffffff;
  font-family: 'Courier New', Courier, monospace;
  letter-spacing: 0.5px;
  font-weight: 600;
}

.status-indicator {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  box-shadow: 0 0 8px currentColor;
}

.success {
  color: var(--cyber-green);
}
.success .dot { background-color: var(--cyber-green); }

.warning {
  color: var(--cyber-yellow);
}
.warning .dot { background-color: var(--cyber-yellow); }

.error {
  color: var(--cyber-red);
}
.error .dot { background-color: var(--cyber-red); }
</style>
