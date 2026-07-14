import { ref, onMounted, onUnmounted } from 'vue';

export function useDashboardScale(wrapperRef, canvasRef, designWidth = 1920, designHeight = 1080) {
  const scale = ref(1);
  let resizeObserver = null;

  const calculateScale = () => {
    if (!wrapperRef.value || !canvasRef.value) return;

    const wrapperWidth = wrapperRef.value.clientWidth;
    const wrapperHeight = wrapperRef.value.clientHeight;

    // Calculate scale to fit the wrapper while maintaining aspect ratio
    const scaleX = wrapperWidth / designWidth;
    const scaleY = wrapperHeight / designHeight;
    
    // We want to fit within the container entirely, so we take the smaller scale
    const finalScale = Math.min(scaleX, scaleY);
    scale.value = finalScale;

    // Apply the scale and center the canvas
    canvasRef.value.style.transform = `translate(-50%, -50%) scale(${finalScale})`;
  };

  onMounted(() => {
    if (wrapperRef.value) {
      resizeObserver = new ResizeObserver(() => {
        requestAnimationFrame(calculateScale);
      });
      resizeObserver.observe(wrapperRef.value);
    }
    calculateScale();
  });

  onUnmounted(() => {
    if (resizeObserver && wrapperRef.value) {
      resizeObserver.unobserve(wrapperRef.value);
      resizeObserver.disconnect();
    }
  });

  return { scale };
}
