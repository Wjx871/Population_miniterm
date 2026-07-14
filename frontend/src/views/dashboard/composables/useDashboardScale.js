import { ref, watch, onMounted, onUnmounted, unref } from 'vue';

/**
 * 混合缩放：
 * - Scale：全屏，或窗口足够大（宽≥1800 且 高≥900）
 * - Scroll：非全屏小窗，允许原生滚动
 */
export function useDashboardScale(
  wrapperRef,
  canvasRef,
  {
    designWidth = 1920,
    designHeight = 1080,
    isFullscreen = false,
    largeMinWidth = 1800,
    largeMinHeight = 900
  } = {}
) {
  const scale = ref(1);
  const mode = ref('scroll'); // 'scale' | 'scroll'
  let resizeObserver = null;
  let rafId = null;

  const shouldScale = (wrapperWidth, wrapperHeight, fullscreen) => {
    if (fullscreen) return true;
    return wrapperWidth >= largeMinWidth && wrapperHeight >= largeMinHeight;
  };

  const applyScaleMode = (wrapperWidth, wrapperHeight) => {
    const canvas = canvasRef.value;
    if (!canvas) return;

    const scaleX = wrapperWidth / designWidth;
    const scaleY = wrapperHeight / designHeight;
    const finalScale = Math.min(scaleX, scaleY);
    scale.value = finalScale;
    mode.value = 'scale';

    canvas.style.width = `${designWidth}px`;
    canvas.style.height = `${designHeight}px`;
    canvas.style.minWidth = `${designWidth}px`;
    canvas.style.minHeight = `${designHeight}px`;
    canvas.style.transform = `translate(-50%, -50%) scale(${finalScale})`;
  };

  const applyScrollMode = () => {
    const canvas = canvasRef.value;
    if (!canvas) return;

    scale.value = 1;
    mode.value = 'scroll';

    canvas.style.width = '';
    canvas.style.height = '';
    canvas.style.minWidth = '';
    canvas.style.minHeight = '';
    canvas.style.transform = '';
  };

  const calculateScale = () => {
    if (!wrapperRef.value || !canvasRef.value) return;

    const wrapperWidth = wrapperRef.value.clientWidth;
    const wrapperHeight = wrapperRef.value.clientHeight;
    const fullscreen = !!unref(isFullscreen);

    if (shouldScale(wrapperWidth, wrapperHeight, fullscreen)) {
      applyScaleMode(wrapperWidth, wrapperHeight);
    } else {
      applyScrollMode();
    }
  };

  const scheduleCalculate = () => {
    if (rafId) cancelAnimationFrame(rafId);
    rafId = requestAnimationFrame(() => {
      calculateScale();
    });
  };

  onMounted(() => {
    if (wrapperRef.value) {
      resizeObserver = new ResizeObserver(() => {
        scheduleCalculate();
      });
      resizeObserver.observe(wrapperRef.value);
    }
    calculateScale();
  });

  // 全屏状态变化时重算
  watch(
    () => unref(isFullscreen),
    () => {
      scheduleCalculate();
    }
  );

  onUnmounted(() => {
    if (rafId) cancelAnimationFrame(rafId);
    if (resizeObserver) {
      resizeObserver.disconnect();
      resizeObserver = null;
    }
  });

  return { scale, mode, recalculate: calculateScale };
}
