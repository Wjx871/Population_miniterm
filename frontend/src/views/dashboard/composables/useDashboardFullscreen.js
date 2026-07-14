import { ref, onMounted, onUnmounted } from 'vue';

export function useDashboardFullscreen() {
  const isFullscreen = ref(false);

  const checkFullscreen = () => {
    isFullscreen.value = !!document.fullscreenElement;
  };

  const toggleFullscreen = async () => {
    try {
      if (!document.fullscreenElement) {
        await document.documentElement.requestFullscreen();
      } else {
        if (document.exitFullscreen) {
          await document.exitFullscreen();
        }
      }
    } catch (err) {
      console.warn('Fullscreen API error:', err);
    }
  };

  onMounted(() => {
    document.addEventListener('fullscreenchange', checkFullscreen);
  });

  onUnmounted(() => {
    document.removeEventListener('fullscreenchange', checkFullscreen);
  });

  return {
    isFullscreen,
    toggleFullscreen
  };
}
