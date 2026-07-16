<template>
  <div class="network-map-panel">
    <div class="visual-frame">
      <span class="frame-corner tl"></span>
      <span class="frame-corner tr"></span>
      <span class="frame-corner bl"></span>
      <span class="frame-corner br"></span>
      <div class="scan-line" aria-hidden="true"></div>
      <div class="glow-ring" aria-hidden="true"></div>

      <video
        src="/media/cyber-video-2.mp4"
        autoplay
        loop
        muted
        playsinline
        disablePictureInPicture
        controlslist="nodownload nofullscreen noremoteplayback"
        class="static-center-video"
        aria-label="中央态势展示视频"
      ></video>
    </div>
  </div>
</template>

<style scoped>
.network-map-panel {
  position: relative;
  width: 100%;
  height: 100%;
  min-height: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}

.visual-frame {
  position: relative;
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px solid rgba(77, 240, 255, 0.35);
  background:
    radial-gradient(ellipse at center, rgba(40, 120, 210, 0.28) 0%, transparent 60%),
    linear-gradient(180deg, rgba(10, 42, 90, 0.55) 0%, rgba(4, 22, 52, 0.7) 100%);
  box-shadow:
    inset 0 0 30px rgba(61, 240, 255, 0.1),
    0 0 24px rgba(61, 240, 255, 0.12);
  overflow: hidden;
}

.frame-corner {
  position: absolute;
  width: 18px;
  height: 18px;
  border: 2px solid var(--cyber-accent);
  z-index: 4;
  pointer-events: none;
}

.frame-corner.tl { top: 6px; left: 6px; border-right: none; border-bottom: none; }
.frame-corner.tr { top: 6px; right: 6px; border-left: none; border-bottom: none; }
.frame-corner.bl { bottom: 6px; left: 6px; border-right: none; border-top: none; }
.frame-corner.br { bottom: 6px; right: 6px; border-left: none; border-top: none; }

.glow-ring {
  position: absolute;
  width: min(62%, 420px);
  aspect-ratio: 1;
  border-radius: 50%;
  border: 1px solid rgba(31, 228, 255, 0.18);
  box-shadow: 0 0 30px rgba(31, 228, 255, 0.12), inset 0 0 30px rgba(31, 228, 255, 0.08);
  pointer-events: none;
  z-index: 1;
  animation: ring-breathe 4.5s ease-in-out infinite;
}

.scan-line {
  position: absolute;
  left: 8%;
  right: 8%;
  height: 2px;
  background: linear-gradient(90deg, transparent, rgba(31, 228, 255, 0.55), transparent);
  box-shadow: 0 0 12px rgba(31, 228, 255, 0.45);
  z-index: 3;
  pointer-events: none;
  animation: scan-move 5.5s linear infinite;
  opacity: 0.55;
}

.static-center-video {
  width: 100%;
  height: 100%;
  object-fit: contain;
  z-index: 2;
  pointer-events: none;
  filter: drop-shadow(0 0 20px rgba(61, 240, 255, 0.35)) brightness(1.08) contrast(1.05);
}

@keyframes scan-move {
  0% { top: 12%; opacity: 0; }
  10% { opacity: 0.55; }
  90% { opacity: 0.45; }
  100% { top: 86%; opacity: 0; }
}

@keyframes ring-breathe {
  0%, 100% { transform: scale(0.96); opacity: 0.55; }
  50% { transform: scale(1.03); opacity: 0.9; }
}
</style>
