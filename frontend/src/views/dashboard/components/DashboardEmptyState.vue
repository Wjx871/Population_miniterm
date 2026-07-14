<template>
  <div class="dashboard-empty-state">
    <div class="empty-icon">
      <el-icon><DataAnalysis /></el-icon>
    </div>
    <div class="empty-text">{{ text }}</div>
  </div>
</template>

<script setup>
import { DataAnalysis } from '@element-plus/icons-vue'

defineProps({
  text: {
    type: String,
    default: '暂无数据'
  }
})
</script>

<style scoped>
.dashboard-empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  width: 100%;
  color: var(--cyber-text-muted);
  opacity: 0.6;
}

.empty-icon {
  font-size: 32px;
  margin-bottom: 8px;
}

.empty-text {
  font-size: 14px;
  letter-spacing: 1px;
}
</style>
