<template>
  <div class="assistant-robot" :class="{ dragging, active: isAssistant }" :style="positionStyle" @pointerdown="startDrag">
    <button class="robot-button" type="button" :aria-label="isAssistant ? '收起政务智能办理助手' : '打开政务智能办理助手'" @click="toggleAssistant">
      <span class="robot-glow"></span>
      <span class="robot-head"><i class="eye left"></i><i class="eye right"></i></span>
      <span class="robot-neck"></span><span class="robot-body"></span>
      <span class="robot-shadow"></span>
    </button>
    <span class="robot-tip">{{ isAssistant ? '收起助手' : '智能办理助手' }}</span>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const router = useRouter(); const route = useRoute(); const dragging = ref(false); const moved = ref(false)
const point = ref({ x: 92, y: 86 }); let start = null
const isAssistant = computed(() => route.path === '/assistant/policy')
const positionStyle = computed(() => ({ left: `${point.value.x}px`, bottom: `${point.value.y}px` }))
function startDrag(event) { if (event.button !== 0) return; start = { x: event.clientX, y: event.clientY, left: point.value.x, bottom: point.value.y }; moved.value = false; dragging.value = true; event.currentTarget.setPointerCapture?.(event.pointerId); window.addEventListener('pointermove', move); window.addEventListener('pointerup', endDrag, { once: true }) }
function move(event) { if (!start) return; const dx = event.clientX - start.x, dy = event.clientY - start.y; if (Math.abs(dx) + Math.abs(dy) > 5) moved.value = true; point.value = { x: Math.max(12, Math.min(window.innerWidth - 88, start.left + dx)), bottom: Math.max(18, Math.min(window.innerHeight - 102, start.bottom - dy)) } }
function endDrag() { dragging.value = false; start = null; window.removeEventListener('pointermove', move) }
function toggleAssistant() { if (moved.value) return; if (isAssistant.value) router.back(); else router.push('/assistant/policy') }
onBeforeUnmount(() => window.removeEventListener('pointermove', move))
</script>

<style scoped>
.assistant-robot{position:fixed;z-index:1000;width:74px;height:94px;touch-action:none;user-select:none;transition:left .32s cubic-bezier(.2,.8,.2,1),bottom .32s cubic-bezier(.2,.8,.2,1),transform .25s ease}.assistant-robot.dragging{transition:none;cursor:grabbing;transform:scale(1.06)}.robot-button{position:relative;width:74px;height:82px;border:0;background:transparent;cursor:grab;padding:0;filter:drop-shadow(0 8px 9px rgba(8,35,57,.23))}.robot-glow{position:absolute;inset:16px 10px 24px;border-radius:50%;background:radial-gradient(circle,rgba(0,230,190,.38),transparent 67%);animation:pulse 2.7s ease-in-out infinite}.robot-head{position:absolute;left:17px;top:3px;width:40px;height:34px;border-radius:18px;background:linear-gradient(145deg,#162631,#071015);border:2px solid #61eed2;box-shadow:0 0 12px rgba(31,232,195,.8),inset 0 -5px 9px rgba(0,0,0,.6);animation:float 3.2s ease-in-out infinite}.eye{position:absolute;top:12px;width:7px;height:10px;border-radius:6px;background:#ccfff4;box-shadow:0 0 6px #31f4d1;animation:blink 4.4s infinite}.eye.left{left:10px}.eye.right{right:10px}.robot-neck{position:absolute;left:25px;top:37px;width:24px;height:8px;border-radius:50%;background:#dde7ea;box-shadow:inset 0 2px 3px #fff}.robot-body{position:absolute;left:15px;top:42px;width:44px;height:35px;border-radius:50% 50% 45% 45%;background:radial-gradient(circle at 38% 25%,#fff,#d9e2e5 58%,#aebdc2);box-shadow:inset -6px -7px 11px rgba(70,90,96,.18);animation:float 3.2s ease-in-out infinite}.robot-shadow{position:absolute;left:16px;bottom:3px;width:42px;height:8px;border-radius:50%;background:rgba(8,36,54,.2);filter:blur(3px);animation:shadow 3.2s ease-in-out infinite}.robot-tip{position:absolute;left:50%;bottom:-13px;transform:translateX(-50%);white-space:nowrap;border-radius:12px;background:#16304a;color:#fff;padding:3px 8px;font-size:11px;opacity:0;transition:opacity .2s;pointer-events:none}.assistant-robot:hover .robot-tip,.assistant-robot.active .robot-tip{opacity:1}@keyframes float{50%{transform:translateY(-5px) rotate(2deg)}}@keyframes shadow{50%{transform:scale(.78);opacity:.12}}@keyframes pulse{50%{transform:scale(1.14);opacity:.65}}@keyframes blink{0%,45%,48%,100%{transform:scaleY(1)}46%,47%{transform:scaleY(.12)}}
@media(max-width:720px){.assistant-robot{left:auto!important;right:18px;bottom:78px!important}}
</style>
