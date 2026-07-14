<template>
  <el-card shadow="never" class="panel">
    <template #header>
      <div class="header">
        <span class="title">{{ title }}</span>
        <el-button class="header-action" v-if="retry" link type="primary" @click="$emit('retry')">重试</el-button>
      </div>
    </template>
    
    <el-alert v-if="error" :title="errorMessage" type="warning" :closable="false" show-icon/>
    
    <div v-else-if="!items.length" class="empty-state">
      <el-icon class="empty-icon"><Document /></el-icon>
      <p>暂无事项</p>
    </div>
    
    <div v-else class="list-wrapper">
      <div v-for="item in items" :key="item.id" class="item">
        <div class="item-content">
          <strong class="item-title">{{ item.title }}</strong>
          <p class="item-meta">{{ item.meta }}</p>
        </div>
        <el-button v-if="item.to" link type="primary" class="item-action" @click="$router.push(item.to)">查看</el-button>
      </div>
    </div>
  </el-card>
</template>

<script setup>
import { computed } from 'vue'
import { Document } from '@element-plus/icons-vue'

const props = defineProps({
  title: String,
  items: { type: Array, default: () => [] },
  error: { type: [Boolean, String], default: false },
  retry: Boolean
})
const errorMessage = computed(() => typeof props.error === 'string' ? props.error : '加载失败，请稍后重试')
defineEmits(['retry'])
</script>

<style scoped>
.panel {
  border-radius: 12px;
  border: none;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.03);
  height: 100%;
}
.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.title {
  font-size: 15px;
  font-weight: 600;
  color: #111827;
}
.header-action {
  font-size: 13px;
  font-weight: 500;
}
:deep(.el-card__header) {
  padding: 16px 20px;
  border-bottom: 1px solid #f3f4f6;
}
:deep(.el-card__body) {
  padding: 0;
}
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px 0;
  color: #9ca3af;
}
.empty-icon {
  font-size: 48px;
  margin-bottom: 12px;
  opacity: 0.5;
  color: #d1d5db;
}
.empty-state p {
  font-size: 13px;
}
.list-wrapper {
  display: flex;
  flex-direction: column;
}
.item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  border-bottom: 1px solid #f9fafb;
}
.item:last-child {
  border-bottom: none;
}
.item-title {
  font-size: 14px;
  font-weight: 500;
  color: #1f2937;
  margin-bottom: 4px;
  display: block;
}
.item-meta {
  font-size: 12px;
  color: #6b7280;
}
.item-action {
  font-size: 13px;
}
</style>
