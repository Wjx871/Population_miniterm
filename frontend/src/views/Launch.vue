<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '../stores/user.js'
import launchVisual from '../assets/images/launch-visual.webp'

const router = useRouter()
const userStore = useUserStore()
const stage = ref(null)
const pageReady = ref(false)
const imageReady = ref(false)
const isLeaving = ref(false)

let targetX = 0
let targetY = 0
let currentX = 0
let currentY = 0
let animationFrame = 0
let navigationTimer = 0
let motionQuery
let reducedMotion = false
let preloader
let sessionRestore = Promise.resolve(false)

function defaultPointer() {
  return {
    x: window.innerWidth * 0.76,
    y: window.innerHeight * 0.3,
  }
}

function writePointer() {
  if (!stage.value) return
  stage.value.style.setProperty('--reveal-x', `${currentX}px`)
  stage.value.style.setProperty('--reveal-y', `${currentY}px`)
}

function animatePointer() {
  if (reducedMotion) {
    currentX = targetX
    currentY = targetY
  } else {
    currentX += (targetX - currentX) * 0.1
    currentY += (targetY - currentY) * 0.1
  }

  writePointer()
  animationFrame = window.requestAnimationFrame(animatePointer)
}

function movePointer(event) {
  targetX = event.clientX
  targetY = event.clientY
}

function resetPointer() {
  const next = defaultPointer()
  targetX = next.x
  targetY = next.y
}

function handleResize() {
  if (!stage.value) return
  const next = defaultPointer()
  targetX = next.x
  targetY = next.y
  if (reducedMotion) {
    currentX = targetX
    currentY = targetY
    writePointer()
  }
}

function handleMotionPreference(event) {
  reducedMotion = event.matches
  if (reducedMotion) {
    currentX = targetX
    currentY = targetY
    writePointer()
  }
}

function enterSystem() {
  if (isLeaving.value) return

  isLeaving.value = true
  if (stage.value) {
    stage.value.style.setProperty('--exit-x', `${currentX}px`)
    stage.value.style.setProperty('--exit-y', `${currentY}px`)
  }

  const delay = reducedMotion ? 80 : 620
  navigationTimer = window.setTimeout(() => {
    sessionRestore.finally(() => {
      router.push(userStore.isLoggedIn ? '/home' : '/login')
    })
  }, delay)
}

onMounted(() => {
  motionQuery = window.matchMedia('(prefers-reduced-motion: reduce)')
  reducedMotion = motionQuery.matches
  motionQuery.addEventListener('change', handleMotionPreference)
  window.addEventListener('resize', handleResize, { passive: true })

  resetPointer()
  currentX = targetX
  currentY = targetY
  writePointer()
  animationFrame = window.requestAnimationFrame(animatePointer)
  window.requestAnimationFrame(() => { pageReady.value = true })

  preloader = new Image()
  preloader.onload = () => { imageReady.value = true }
  preloader.onerror = () => { imageReady.value = false }
  preloader.src = launchVisual

  if (userStore.accessToken && !userStore.sessionChecked) {
    sessionRestore = userStore.restoreSession().catch(() => false)
  }
})

onBeforeUnmount(() => {
  window.cancelAnimationFrame(animationFrame)
  window.clearTimeout(navigationTimer)
  window.removeEventListener('resize', handleResize)
  motionQuery?.removeEventListener('change', handleMotionPreference)
  if (preloader) {
    preloader.onload = null
    preloader.onerror = null
  }
})
</script>

<template>
  <main
    ref="stage"
    class="launch"
    :class="{
      'is-page-ready': pageReady,
      'is-image-ready': imageReady,
      'is-leaving': isLeaving,
    }"
    @pointermove="movePointer"
    @pointerleave="resetPointer"
  >
    <div
      class="visual visual-base"
      :style="{ backgroundImage: `url(${launchVisual})` }"
      aria-hidden="true"
    />
    <div
      class="visual visual-reveal"
      :style="{ backgroundImage: `url(${launchVisual})` }"
      aria-hidden="true"
    />
    <div
      class="visual visual-exit"
      :style="{ backgroundImage: `url(${launchVisual})` }"
      aria-hidden="true"
    />
    <div class="visual-shade" aria-hidden="true" />

    <section class="launch-copy" aria-labelledby="launch-title">
      <h1 id="launch-title">人口数据库管理系统</h1>
      <p>Population Management System</p>
      <button type="button" :disabled="isLeaving" @click="enterSystem">
        <span>进入系统</span>
        <span class="button-arrow" aria-hidden="true">→</span>
      </button>
    </section>
  </main>
</template>

<style scoped>
.launch {
  --reveal-x: 76vw;
  --reveal-y: 30vh;
  --exit-x: 76vw;
  --exit-y: 30vh;
  --reveal-radius: clamp(210px, 23vw, 390px);
  position: fixed;
  inset: 0;
  isolation: isolate;
  min-height: 100vh;
  min-height: 100svh;
  overflow: hidden;
  color: #f4f7fb;
  background:
    radial-gradient(circle at 76% 30%, rgba(18, 63, 84, 0.3), transparent 34%),
    linear-gradient(128deg, #020508 0%, #071019 48%, #04070a 100%);
  touch-action: pan-x pan-y;
}

.visual {
  position: absolute;
  z-index: -4;
  inset: -1.5%;
  background-position: center;
  background-repeat: no-repeat;
  background-size: cover;
  opacity: 0;
  transform: scale(1.025);
  will-change: opacity, filter, transform;
}

.is-image-ready .visual {
  opacity: 1;
}

.visual-base {
  filter: saturate(0.16) brightness(0.35) contrast(1.18) blur(0.45px);
  transition: opacity 900ms ease, filter 620ms ease;
}

.visual-reveal {
  z-index: -3;
  filter: saturate(1.04) brightness(0.96) contrast(1.08);
  -webkit-mask-image: radial-gradient(
    circle var(--reveal-radius) at var(--reveal-x) var(--reveal-y),
    #000 0%,
    #000 48%,
    rgba(0, 0, 0, 0.9) 64%,
    rgba(0, 0, 0, 0.34) 82%,
    transparent 100%
  );
  mask-image: radial-gradient(
    circle var(--reveal-radius) at var(--reveal-x) var(--reveal-y),
    #000 0%,
    #000 48%,
    rgba(0, 0, 0, 0.9) 64%,
    rgba(0, 0, 0, 0.34) 82%,
    transparent 100%
  );
}

.visual-exit {
  z-index: -2;
  filter: saturate(1.08) brightness(1) contrast(1.08);
  clip-path: circle(0 at var(--exit-x) var(--exit-y));
  transition: clip-path 620ms cubic-bezier(0.65, 0, 0.18, 1);
}

.is-leaving .visual-exit {
  clip-path: circle(150vmax at var(--exit-x) var(--exit-y));
}

.visual-shade {
  position: absolute;
  z-index: -1;
  inset: 0;
  pointer-events: none;
  background:
    linear-gradient(90deg, rgba(1, 5, 8, 0.68) 0%, rgba(2, 7, 11, 0.26) 37%, transparent 66%),
    linear-gradient(180deg, rgba(0, 2, 4, 0.13), transparent 55%, rgba(0, 2, 4, 0.3));
}

.launch-copy {
  position: absolute;
  top: 50%;
  left: clamp(28px, 6.4vw, 112px);
  width: min(620px, 44vw);
  transform: translateY(-46%);
}

.launch-copy h1,
.launch-copy p,
.launch-copy button {
  opacity: 0;
  transform: translateY(22px);
  filter: blur(6px);
  transition:
    opacity 760ms cubic-bezier(0.22, 1, 0.36, 1),
    transform 760ms cubic-bezier(0.22, 1, 0.36, 1),
    filter 760ms cubic-bezier(0.22, 1, 0.36, 1);
}

.launch-copy h1 {
  margin: 0;
  max-width: 10em;
  font-family: "Microsoft YaHei", "PingFang SC", sans-serif;
  font-size: clamp(42px, 5vw, 80px);
  font-weight: 300;
  line-height: 1.18;
  letter-spacing: 0.08em;
  text-wrap: balance;
  text-shadow: 0 2px 28px rgba(0, 0, 0, 0.36);
}

.launch-copy p {
  margin: 20px 0 0;
  color: rgba(229, 239, 247, 0.6);
  font-family: Arial, Helvetica, sans-serif;
  font-size: clamp(11px, 0.95vw, 15px);
  font-weight: 400;
  line-height: 1.5;
  letter-spacing: 0.32em;
  text-transform: uppercase;
  transition-delay: 90ms;
}

.launch-copy button {
  position: relative;
  display: inline-flex;
  align-items: center;
  gap: 18px;
  margin-top: clamp(44px, 7vh, 72px);
  padding: 12px 0 14px;
  overflow: visible;
  color: #f5f9fc;
  font: inherit;
  font-size: 15px;
  font-weight: 500;
  letter-spacing: 0.18em;
  background: transparent;
  border: 0;
  border-bottom: 1px solid rgba(211, 229, 240, 0.42);
  border-radius: 0;
  cursor: pointer;
  transition-delay: 170ms;
}

.launch-copy button::after {
  position: absolute;
  right: 0;
  bottom: -1px;
  left: 0;
  height: 1px;
  content: "";
  background: linear-gradient(90deg, #65d9f2, #f2a252);
  transform: scaleX(0);
  transform-origin: right;
  transition: transform 280ms cubic-bezier(0.22, 1, 0.36, 1);
}

.button-arrow {
  display: inline-block;
  font-size: 20px;
  font-weight: 300;
  line-height: 1;
  transition: transform 280ms cubic-bezier(0.22, 1, 0.36, 1);
}

.launch-copy button:hover::after,
.launch-copy button:focus-visible::after {
  transform: scaleX(1);
  transform-origin: left;
}

.launch-copy button:hover .button-arrow,
.launch-copy button:focus-visible .button-arrow {
  transform: translateX(6px);
}

.launch-copy button:active {
  transform: translateY(1px);
}

.launch-copy button:focus-visible {
  outline: 2px solid rgba(105, 218, 243, 0.9);
  outline-offset: 8px;
}

.launch-copy button:disabled {
  cursor: default;
}

.is-page-ready .launch-copy h1,
.is-page-ready .launch-copy p,
.is-page-ready .launch-copy button {
  opacity: 1;
  transform: translateY(0);
  filter: blur(0);
}

.is-leaving .launch-copy {
  opacity: 0;
  transform: translateY(-46%) scale(0.985);
  transition: opacity 260ms ease, transform 520ms cubic-bezier(0.65, 0, 0.18, 1);
}

@media (max-width: 760px) {
  .launch {
    --reveal-radius: min(46vmax, 340px);
  }

  .visual {
    inset: -2%;
    background-position: 58% center;
  }

  .visual-shade {
    background:
      linear-gradient(180deg, rgba(1, 5, 8, 0.08) 0%, rgba(1, 5, 8, 0.18) 44%, rgba(1, 5, 8, 0.86) 100%),
      linear-gradient(90deg, rgba(1, 5, 8, 0.35), transparent 78%);
  }

  .launch-copy {
    top: auto;
    right: 24px;
    bottom: max(48px, calc(env(safe-area-inset-bottom) + 28px));
    left: 24px;
    width: auto;
    transform: none;
  }

  .launch-copy h1 {
    max-width: 9em;
    font-size: clamp(34px, 10.4vw, 52px);
    line-height: 1.2;
    letter-spacing: 0.05em;
  }

  .launch-copy p {
    margin-top: 14px;
    font-size: 10px;
    letter-spacing: 0.2em;
  }

  .launch-copy button {
    min-height: 44px;
    margin-top: 36px;
  }

  .is-leaving .launch-copy {
    transform: scale(0.985);
  }
}

@media (hover: none) and (pointer: coarse) {
  .launch {
    --reveal-radius: min(54vmax, 440px);
  }
}

@media (prefers-reduced-motion: reduce) {
  .visual,
  .launch-copy,
  .launch-copy h1,
  .launch-copy p,
  .launch-copy button,
  .launch-copy button::after,
  .button-arrow {
    transition-duration: 80ms !important;
  }

  .visual {
    transform: none;
  }

  .visual-reveal {
    -webkit-mask-image: radial-gradient(
      circle min(46vmax, 520px) at var(--reveal-x) var(--reveal-y),
      #000 0%,
      #000 64%,
      transparent 100%
    );
    mask-image: radial-gradient(
      circle min(46vmax, 520px) at var(--reveal-x) var(--reveal-y),
      #000 0%,
      #000 64%,
      transparent 100%
    );
  }

  .is-image-ready .visual-exit {
    transition: opacity 80ms linear !important;
    clip-path: none;
    opacity: 0;
  }

  .is-image-ready.is-leaving .visual-exit {
    opacity: 1;
  }
}
</style>
