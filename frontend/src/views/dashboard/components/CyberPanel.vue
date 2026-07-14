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
  background:
    radial-gradient(circle at 8% 0%, rgba(61, 240, 255, 0.12) 0%, transparent 35%),
    linear-gradient(180deg, rgba(12, 48, 96, 0.95) 0%, rgba(6, 28, 62, 0.96) 100%);
  border: 1px solid rgba(77, 240, 255, 0.28);
  box-shadow:
    inset 0 0 22px rgba(61, 240, 255, 0.08),
    0 0 18px rgba(61, 240, 255, 0.1);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

/* 顶部流光 */
.cyber-panel::before {
  content: '';
  position: absolute;
  top: 0;
  left: -40%;
  width: 40%;
  height: 2px;
  background: linear-gradient(90deg, transparent, rgba(61, 240, 255, 0.95), transparent);
  box-shadow: 0 0 12px rgba(61, 240, 255, 0.8);
  animation: panel-flow 5.5s linear infinite;
  z-index: 4;
  pointer-events: none;
}

@keyframes panel-flow {
  0% { left: -40%; opacity: 0; }
  15% { opacity: 1; }
  85% { opacity: 1; }
  100% { left: 100%; opacity: 0; }
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
  box-shadow: 0 0 8px rgba(61, 240, 255, 0.45);
  animation: corner-pulse 3.2s ease-in-out infinite;
}

@keyframes corner-pulse {
  0%, 100% { opacity: 0.65; filter: brightness(1); }
  50% { opacity: 1; filter: brightness(1.35); }
}

.top-left { top: -1px; left: -1px; border-right: none; border-bottom: none; }
.top-right { top: -1px; right: -1px; border-left: none; border-bottom: none; animation-delay: 0.4s; }
.bottom-left { bottom: -1px; left: -1px; border-right: none; border-top: none; animation-delay: 0.8s; }
.bottom-right { bottom: -1px; right: -1px; border-left: none; border-top: none; animation-delay: 1.2s; }

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
  padding: 0 40px 0 18px;
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
  margin-left: 12px;
}

.title-icon {
  font-size: 16px;
  color: var(--cyber-accent);
}

.title-text {
  font-size: 16px;
  font-weight: 700;
  color: #ffffff;
  letter-spacing: 1px;
  text-shadow: 0 0 10px rgba(61, 240, 255, 0.65);
  padding-left: 4px;
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
