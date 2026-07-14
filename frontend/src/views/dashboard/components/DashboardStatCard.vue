<template>
  <article class="stat-card">
    <div class="stat-content">
      <p class="stat-label">{{ label }}</p>
      <strong class="stat-value">{{ value === null ? '—' : value }}</strong>
      <small v-if="hint" class="stat-hint">{{ hint }}</small>
    </div>
    <div class="stat-icon-wrapper" :class="colorTheme" v-if="icon">
      <el-icon><component :is="icon" /></el-icon>
    </div>
  </article>
</template>
<script setup>
defineProps({
  label: String,
  value: { type: [Number, String], default: null },
  hint: String,
  icon: { type: [Object, String], default: null },
  colorTheme: { type: String, default: 'blue' }
})
</script>
<style scoped>
.stat-card {
  display: flex;
  justify-content: space-between;
  align-items: center;
  min-height: 110px;
  padding: 24px;
  border-radius: 12px;
  background: #ffffff;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.03);
  border: none;
}
.stat-content {
  display: flex;
  flex-direction: column;
}
.stat-label {
  font-size: 14px;
  color: #6b7280;
  margin-bottom: 16px;
}
.stat-value {
  font-size: 32px;
  color: #111827;
  font-weight: 600;
  line-height: 1;
}
.stat-hint {
  color: #9ca3af;
  margin-top: 8px;
  font-size: 12px;
}
.stat-icon-wrapper {
  width: 52px;
  height: 52px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
}
/* Color Themes */
.blue { background: #e6f4ff; color: #1677ff; }
.teal { background: #e6fffb; color: #13c2c2; }
.orange { background: #fff7e6; color: #fa8c16; }
.purple { background: #f9f0ff; color: #722ed1; }
.red { background: #fff1f0; color: #f5222d; }
.green { background: #f6ffed; color: #52c41a; }
</style>
