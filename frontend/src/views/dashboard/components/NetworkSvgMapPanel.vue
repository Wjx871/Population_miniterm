<template>
  <div class="network-map-panel">
    <!-- 替换为指定的科幻视频 -->
    <video src="/media/7月14日.mp4" autoplay loop muted class="static-center-video" alt="中央态势分布图"></video>
  </div>
</template>

<script setup>
// 声明 props 以防止 Vue 把数组当做普通 HTML 属性渲染到 DOM 上
defineProps({
  data: {
    type: Array,
    default: () => []
  }
})
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

.static-center-video {
  width: 100%;
  height: 100%;
  object-fit: contain;
  z-index: 10;
  filter: drop-shadow(0 0 30px rgba(0, 229, 255, 0.5)) brightness(1.2);
  /* 确保黑底色融入深蓝背景 */
  mix-blend-mode: screen;
}

@keyframes float-map {
  0% { transform: translateY(0) scale(1); }
  50% { transform: translateY(-8px) scale(1.01); }
  100% { transform: translateY(0) scale(1); }
}
</style>
