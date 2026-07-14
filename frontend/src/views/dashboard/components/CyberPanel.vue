<template>
  <div class="cyber-panel">
    <!-- 角落装饰线 -->
    <div class="corner top-left"></div>
    <div class="corner top-right"></div>
    <div class="corner bottom-left"></div>
    <div class="corner bottom-right"></div>
    
    <div class="panel-header" v-if="title || $slots.header">
      <div class="header-title">
        <span class="title-text">{{ title }}</span>
        <slot name="header"></slot>
      </div>
      <div class="header-extra">
        <slot name="extra"></slot>
      </div>
    </div>
    
    <div class="panel-content" :class="{ 'no-padding': noPadding }">
      <slot></slot>
    </div>
  </div>
</template>

<script setup>
defineProps({
  title: {
    type: String,
    default: ''
  },
  noPadding: {
    type: Boolean,
    default: false
  }
})
</script>

<style scoped>
.cyber-panel {
  position: relative;
  background: var(--cyber-panel-bg);
  border: 1px solid var(--cyber-border-color);
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.2);
  display: flex;
  flex-direction: column;
  height: 100%;
}

.corner {
  position: absolute;
  width: 12px;
  height: 12px;
  border: 2px solid var(--cyber-accent);
  opacity: 0.8;
  z-index: 2;
}

.top-left {
  top: -1px;
  left: -1px;
  border-right: none;
  border-bottom: none;
}

.top-right {
  top: -1px;
  right: -1px;
  border-left: none;
  border-bottom: none;
}

.bottom-left {
  bottom: -1px;
  left: -1px;
  border-right: none;
  border-top: none;
}

.bottom-right {
  bottom: -1px;
  right: -1px;
  border-left: none;
  border-top: none;
}

.panel-header {
  height: 44px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 16px;
  border-bottom: 1px solid rgba(41, 215, 255, 0.15);
  background: linear-gradient(90deg, rgba(41, 215, 255, 0.1) 0%, transparent 100%);
}

.header-title {
  display: flex;
  align-items: center;
  gap: 8px;
}

.title-text {
  font-size: 16px;
  font-weight: 500;
  color: var(--cyber-accent);
  letter-spacing: 1px;
  text-shadow: 0 0 8px rgba(41, 215, 255, 0.4);
}

.title-text::before {
  content: '';
  display: inline-block;
  width: 4px;
  height: 14px;
  background: var(--cyber-accent);
  margin-right: 8px;
  border-radius: 2px;
}

.panel-content {
  flex: 1;
  padding: 16px;
  position: relative;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.panel-content.no-padding {
  padding: 0;
}
</style>
