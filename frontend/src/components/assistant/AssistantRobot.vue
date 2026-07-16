<template>
  <div class="assistant-entry" :class="{ dragging, active: isAssistant }" :style="positionStyle" @pointerdown="startDrag" @pointerup="activate">
    <button type="button" class="entry-button" aria-label="打开政务智能办理助手"><RobotMascot compact /></button>
    <span class="entry-label">{{ isAssistant ? '政务智能助手' : '点击咨询政策' }}</span>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import RobotMascot from './RobotMascot.vue'

const router = useRouter(); const route = useRoute(); const dragging = ref(false); const moved = ref(false)
const point = ref({ x: 88, y: 84 }); let start = null
const isAssistant = computed(() => route.path === '/assistant/policy')
const positionStyle = computed(() => ({ left: `${point.value.x}px`, bottom: `${point.value.y}px` }))
function startDrag(event) { if (event.button !== 0) return; start = { x: event.clientX, y: event.clientY, left: point.value.x, bottom: point.value.y }; moved.value = false; dragging.value = true; event.currentTarget.setPointerCapture?.(event.pointerId); window.addEventListener('pointermove', move); window.addEventListener('pointerup', endDrag, { once: true }) }
function move(event) { if (!start) return; const dx = event.clientX - start.x, dy = event.clientY - start.y; if (Math.abs(dx) + Math.abs(dy) > 5) moved.value = true; point.value = { x: Math.max(18, Math.min(158, start.left + dx)), bottom: Math.max(76, Math.min(window.innerHeight - 120, start.bottom - dy)) } }
function endDrag() { dragging.value = false; start = null; window.removeEventListener('pointermove', move) }
function activate() { if (moved.value) return; if (isAssistant.value) router.push('/home').catch(() => {}); else router.push('/assistant/policy').catch(() => {}) }
onBeforeUnmount(() => window.removeEventListener('pointermove', move))
</script>

<style scoped>
.assistant-entry{position:fixed;z-index:1000;width:78px;height:114px;touch-action:none;user-select:none;transition:left .3s cubic-bezier(.2,.8,.2,1),bottom .3s cubic-bezier(.2,.8,.2,1),transform .2s}.assistant-entry.dragging{transition:none;cursor:grabbing;transform:scale(1.05)}.entry-button{border:0;background:transparent;padding:0;width:78px;height:92px;cursor:grab}.entry-label{position:absolute;left:50%;bottom:0;translate:-50%;white-space:nowrap;border-radius:12px;background:#112b40;color:#effffc;padding:4px 8px;font-size:11px;opacity:0;transform:translateY(3px);transition:.2s;pointer-events:none;box-shadow:0 5px 14px rgba(7,29,47,.2)}.assistant-entry:hover .entry-label,.assistant-entry.active .entry-label{opacity:1;transform:translateY(0)}@media(max-width:720px){.assistant-entry{left:auto!important;right:18px;bottom:78px!important}}
</style>
