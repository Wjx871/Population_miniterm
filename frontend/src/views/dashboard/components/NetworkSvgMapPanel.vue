<template>
  <div class="network-map-panel">
    <!-- 替换为生成的科幻地理区域图 -->
    <img src="../../../assets/images/geo-map.jpg" class="static-center-map" alt="中央态势分布图" />
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
  width: 100%;
  height: 100%;
  object-fit: contain;
  z-index: 10;
  animation: float-map 6s ease-in-out infinite;
  filter: drop-shadow(0 0 30px rgba(41, 215, 255, 0.5)) brightness(1.2);
  /* 确保底色融入深蓝背景 */
  mix-blend-mode: screen;
}

@keyframes float-map {
  0% { transform: translateY(0) scale(1); }
  50% { transform: translateY(-8px) scale(1.01); }
  100% { transform: translateY(0) scale(1); }
}
</style>
