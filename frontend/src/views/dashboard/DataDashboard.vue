<template>
  <div ref="wrapperRef" class="dashboard-wrapper">
    <div ref="canvasRef" class="dashboard-canvas">
      <!-- 顶部 Header -->
      <DashboardScreenHeader 
        :is-fullscreen="isFullscreen" 
        :is-demo="false"
        :loading="false"
        @toggle-fullscreen="toggleFullscreen"
      />

      <!-- 主要内容区骨架 -->
      <main class="dashboard-content-skeleton">
        <div class="column left-column">
          <div class="panel">面板 1</div>
          <div class="panel">面板 2</div>
        </div>
        <div class="column center-column">
          <div class="panel center-panel">中央态势图</div>
        </div>
        <div class="column right-column">
          <div class="panel">面板 3</div>
          <div class="panel">面板 4</div>
        </div>
      </main>

      <!-- 底部 Footer -->
      <DashboardFooter />
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue';
import { useDashboardScale } from './composables/useDashboardScale';
import { useDashboardFullscreen } from './composables/useDashboardFullscreen';
import DashboardScreenHeader from './components/DashboardScreenHeader.vue';
import DashboardFooter from './components/DashboardFooter.vue';

const wrapperRef = ref(null);
const canvasRef = ref(null);

// 绑定缩放
useDashboardScale(wrapperRef, canvasRef, 1920, 1080);

// 绑定全屏
const { isFullscreen, toggleFullscreen } = useDashboardFullscreen();
</script>

<style scoped>
@import './styles/dashboard-screen.css';

.dashboard-wrapper {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background-color: #020f28; /* 深色背景 */
  overflow: hidden;
}

.dashboard-canvas {
  position: absolute;
  top: 50%;
  left: 50%;
  width: 1920px;
  height: 1080px;
  transform-origin: center center;
  /* js 会动态写入 transform: translate(-50%, -50%) scale(xxx); */
  display: flex;
  flex-direction: column;
  color: white;
  background-image: radial-gradient(circle at center, #05193c 0%, #020f28 100%);
}

.dashboard-content-skeleton {
  flex: 1;
  display: flex;
  gap: 20px;
  padding: 20px;
}

.column {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.left-column, .right-column {
  width: 450px;
}

.center-column {
  flex: 1;
}

.panel {
  background: rgba(10, 30, 70, 0.5);
  border: 1px solid rgba(41, 215, 255, 0.2);
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex: 1;
  font-size: 24px;
  color: rgba(255, 255, 255, 0.5);
}
</style>
