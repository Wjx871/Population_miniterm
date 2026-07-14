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
  height: 100%;
  padding: 20px 26px;
  border-radius: 12px;
  background: #ffffff;
  box-shadow: 0 6px 18px rgba(36, 67, 109, 0.045);
  border: 1px solid rgba(235, 239, 245, .82);
}
.stat-content {
  display: flex;
  flex-direction: column;
}
.stat-label {
  font-size: 14px;
  color: #657a99;
  margin-bottom: 8px;
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
  width: 64px;
  height: 64px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28px;
  position: relative;
  flex: 0 0 auto;
}
.stat-icon-wrapper::before {
  content: '';
  position: absolute;
  left: -22px;
  top: 14px;
  width: 1px;
  height: 36px;
  background: #e2e8f2;
}
/* Color Themes */
.blue { background: #e6f4ff; color: #1677ff; }
.teal { background: #e6fffb; color: #13c2c2; }
.orange { background: #fff7e6; color: #fa8c16; }
.purple { background: #f9f0ff; color: #722ed1; }
.red { background: #fff1f0; color: #f5222d; }
.green { background: #f6ffed; color: #52c41a; }
</style>
