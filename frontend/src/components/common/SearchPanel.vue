<template>
  <el-card shadow="never" class="search-panel">
    <div class="search-content">
      <div class="search-form">
        <slot></slot>
      </div>
      <div class="search-actions">
        <el-button type="primary" @click="$emit('search')">查询</el-button>
        <el-button @click="$emit('reset')">重置</el-button>
      </div>
    </div>
  </el-card>
</template>

<script setup>
defineEmits(['search', 'reset']);
</script>

<style scoped>
.search-panel {
  margin-bottom: 16px;
  border-radius: var(--radius-large);
}
.search-content {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  flex-wrap: wrap;
  gap: 16px;
}
.search-form {
  flex: 1;
}
.search-form :deep(.el-form-item) {
  margin-bottom: 0;
  margin-right: 24px;
}
.search-actions {
  display: flex;
  gap: 12px;
}
</style>
