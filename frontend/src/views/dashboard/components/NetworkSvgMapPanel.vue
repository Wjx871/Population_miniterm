<template>
  <div class="network-map-panel">
    <!-- 替换为生成的静态特效图 -->
    <img src="@/assets/images/center-map.jpg" class="static-center-map" alt="中央态势分布图" />
    
    <!-- 装饰光环 -->
    <div class="glow-ring glow-1"></div>
    <div class="glow-ring glow-2"></div>
    <div class="glow-ring glow-3"></div>
  </div>
</template>

<script setup>
// 使用静态图片代替 ECharts 渲染，保留周围的光环动效
</script>

<style scoped>
.network-map-panel {
  position: relative;
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}

.static-center-map {
  width: 80%;
  max-width: 500px;
  max-height: 500px;
  object-fit: contain;
  border-radius: 50%;
  z-index: 10;
  animation: float-map 6s ease-in-out infinite;
  filter: drop-shadow(0 0 20px rgba(41, 215, 255, 0.4));
  /* 确保底色融入深蓝背景 */
  mix-blend-mode: screen;
}

@keyframes float-map {
  0% { transform: translateY(0) scale(1); }
  50% { transform: translateY(-10px) scale(1.02); }
  100% { transform: translateY(0) scale(1); }
}

/* 底部发光光环 */
.glow-ring {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  border-radius: 50%;
  border: 1px solid rgba(41, 215, 255, 0.1);
  pointer-events: none;
  z-index: 0;
}

.glow-1 {
  width: 300px;
  height: 300px;
  background: radial-gradient(circle, rgba(41, 215, 255, 0.05) 0%, transparent 70%);
  animation: pulse 4s infinite alternate;
}

.glow-2 {
  width: 450px;
  height: 450px;
  border-style: dashed;
  opacity: 0.5;
  animation: spin 30s linear infinite;
}

.glow-3 {
  width: 600px;
  height: 600px;
  border-color: rgba(59, 130, 246, 0.2);
  animation: spin-reverse 40s linear infinite;
}

@keyframes pulse {
  0% { transform: translate(-50%, -50%) scale(0.95); opacity: 0.8; }
  100% { transform: translate(-50%, -50%) scale(1.05); opacity: 0.4; }
}

@keyframes spin {
  from { transform: translate(-50%, -50%) rotate(0deg); }
  to { transform: translate(-50%, -50%) rotate(360deg); }
}

@keyframes spin-reverse {
  from { transform: translate(-50%, -50%) rotate(360deg); }
  to { transform: translate(-50%, -50%) rotate(0deg); }
}
</style>
