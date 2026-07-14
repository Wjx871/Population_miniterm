<template>
  <el-card shadow="never" class="panel">
    <template #header>
      <div class="header">
        <span class="title">常用入口</span>
      </div>
    </template>
    <div class="actions">
      <div class="action-item" v-for="item in actions" :key="item.to" @click="$router.push(item.to)">
        <el-icon class="action-icon"><component :is="item.icon"/></el-icon>
        <span>{{ item.label }}</span>
      </div>
    </div>
  </el-card>
</template>
<script setup>
defineProps({
  actions: { type: Array, default: () => [] }
})
</script>
<style scoped>
.panel {
  border-radius: 12px;
  border: none;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.03);
}
.header {
  font-size: 15px;
  font-weight: 600;
  color: #111827;
}
:deep(.el-card__header) {
  padding: 16px 20px 8px;
  border-bottom: 1px solid #f3f4f6;
}
:deep(.el-card__body) {
  padding: 6px 20px 16px;
}
.actions {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}
.action-item {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 11px 16px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  cursor: pointer;
  color: #374151;
  font-size: 13px;
  transition: all 0.2s;
  background: transparent;
}
.action-item:hover {
  border-color: #91caff;
  color: #1677ff;
  background: #f0f7ff;
}
.action-icon {
  font-size: 16px;
  color: #1677ff;
}
</style>
