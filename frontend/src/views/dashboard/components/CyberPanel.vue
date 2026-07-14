<template>
  <div class="cyber-panel">
    <!-- 装饰边框（通过复杂的CSS阴影和裁剪实现机甲感） -->
    <div class="panel-border-top"></div>
    <div class="panel-border-bottom"></div>
    <div class="panel-border-left"></div>
    <div class="panel-border-right"></div>
    
    <div class="corner-decor top-left"></div>
    <div class="corner-decor top-right"></div>
    <div class="corner-decor bottom-left"></div>
    <div class="corner-decor bottom-right"></div>
    
    <div class="panel-inner">
      <div class="panel-header" v-if="title || $slots.header">
        <div class="header-title-bg">
          <div class="header-title">
            <el-icon v-if="icon" class="title-icon"><component :is="icon" /></el-icon>
            <span class="title-text">{{ title }}</span>
            <slot name="header"></slot>
          </div>
        </div>
        <div class="header-extra">
          <slot name="extra"></slot>
        </div>
      </div>
      
      <div class="panel-content" :class="{ 'no-padding': noPadding }">
        <slot></slot>
      </div>
    </div>
  </div>
</template>

<script setup>
defineProps({
  title: {
    type: String,
    default: ''
  },
  icon: {
    type: Object,
    default: null
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
  height: 100%;
  min-height: 0;
  padding: 1px;
  background: var(--cyber-panel-bg);
  box-shadow:
    inset 0 0 22px rgba(31, 228, 255, 0.05),
    0 0 14px rgba(31, 228, 255, 0.05);
  display: flex;
  flex-direction: column;
}

/* 核心实线边框（由四个伪元素构成） */
.panel-border-top, .panel-border-bottom {
  position: absolute;
  left: 10px;
  right: 10px;
  height: 1px;
  background: var(--cyber-border-color);
  z-index: 2;
}
.panel-border-top { top: 0; }
.panel-border-bottom { bottom: 0; }

.panel-border-left, .panel-border-right {
  position: absolute;
  top: 10px;
  bottom: 10px;
  width: 1px;
  background: var(--cyber-border-color);
  z-index: 2;
}
.panel-border-left { left: 0; }
.panel-border-right { right: 0; }

/* 边角高亮装饰 */
.corner-decor {
  position: absolute;
  width: 16px;
  height: 16px;
  border: 2px solid var(--cyber-accent);
  z-index: 3;
}

.top-left { top: -1px; left: -1px; border-right: none; border-bottom: none; }
.top-right { top: -1px; right: -1px; border-left: none; border-bottom: none; }
.bottom-left { bottom: -1px; left: -1px; border-right: none; border-top: none; }
.bottom-right { bottom: -1px; right: -1px; border-left: none; border-top: none; }

.panel-inner {
  flex: 1;
  display: flex;
  flex-direction: column;
  position: relative;
  z-index: 1;
}

.panel-header {
  height: 40px;
  flex-shrink: 0;
  display: flex;
  justify-content: space-between;
  align-items: center;
  position: relative;
  margin: 10px 10px 0 10px;
  border-bottom: 1px solid rgba(31, 228, 255, 0.16);
}

.header-title-bg {
  height: 100%;
  display: flex;
  align-items: center;
  background: linear-gradient(90deg, rgba(31, 228, 255, 0.16) 0%, transparent 100%);
  padding: 0 40px 0 10px;
  clip-path: polygon(0 0, calc(100% - 15px) 0, 100% 100%, 0% 100%);
  position: relative;
}

.header-title-bg::before {
  content: '';
  position: absolute;
  left: 0;
  top: 6px;
  bottom: 6px;
  width: 3px;
  background: var(--cyber-accent);
  box-shadow: 0 0 8px var(--cyber-accent-glow);
}

.header-title {
  display: flex;
  align-items: center;
  gap: 8px;
}

.title-icon {
  font-size: 16px;
  color: var(--cyber-accent);
}

.title-text {
  font-size: 15px;
  font-weight: 600;
  color: var(--cyber-text-primary);
  letter-spacing: 1px;
  text-shadow: 0 0 8px rgba(31, 228, 255, 0.55);
}

.header-extra {
  padding-right: 10px;
}

.panel-content {
  flex: 1;
  min-height: 0;
  padding: 14px;
  position: relative;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.panel-content.no-padding {
  padding: 0;
}
</style>
