<template>
  <footer class="dashboard-footer">
    <div class="footer-left">
      <span class="label">数据来源：</span>
      <span class="value">人口数据库管理系统</span>
    </div>
    <div class="footer-center">
      <span class="label">系统运行状态：</span>
      <span class="status-indicator" :class="statusClass">
        <span class="dot"></span>
        {{ statusText }}
      </span>
    </div>
    <div class="footer-right">
      <span class="label">数据更新时间：</span>
      <span class="value">{{ updateTime || '未加载' }}</span>
    </div>
  </footer>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  updateTime: String,
  overviewError: Boolean,
  chartsError: Boolean
})

const statusClass = computed(() => {
  if (props.overviewError && props.chartsError) return 'error'
  if (props.overviewError || props.chartsError) return 'warning'
  return 'success'
})

const statusText = computed(() => {
  if (props.overviewError && props.chartsError) return '数据加载失败'
  if (props.overviewError || props.chartsError) return '部分数据异常'
  return '正常'
})
</script>

<style scoped>
.dashboard-footer {
  height: 40px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 40px;
  font-size: 14px;
  color: var(--cyber-text-secondary);
  border-top: 1px solid rgba(41, 215, 255, 0.1);
  background: linear-gradient(0deg, rgba(2, 15, 40, 0.8) 0%, transparent 100%);
}

.label {
  color: var(--cyber-text-muted);
}

.value {
  color: var(--cyber-text-primary);
  font-family: 'Courier New', Courier, monospace;
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
