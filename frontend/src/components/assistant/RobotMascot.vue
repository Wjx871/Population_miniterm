<template>
  <div ref="root" class="robot-mascot" :class="{ compact }" :style="motionStyle">
    <div class="robot-shadow"></div>
    <div class="robot-motion">
      <div class="robot-antenna antenna-left"><i></i></div><div class="robot-antenna antenna-right"><i></i></div>
      <div class="robot-ear ear-left"><span></span></div><div class="robot-ear ear-right"><span></span></div>
      <div class="robot-head">
        <div class="head-glass"></div>
        <div class="robot-eyes" :style="eyeStyle"><i></i><i></i></div>
      </div>
      <div class="robot-neck"></div>
      <div class="robot-body"><div class="body-grain"></div></div>
    </div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'

defineProps({ compact: { type: Boolean, default: false } })
const root = ref(null); const pointer = ref({ x: 0, y: 0 })
// 视线覆盖整个屏幕；机身以更慢、更小的幅度平行追随光标。
const motionStyle = computed(() => ({ '--robot-x': `${pointer.value.x * 11}px`, '--robot-tilt': `${pointer.value.x * 2.2}deg` }))
const eyeStyle = computed(() => ({ transform: `translate(${pointer.value.x * 28}px, ${pointer.value.y * 18}px)` }))
function followPointer(event) { pointer.value = { x: Math.max(-1, Math.min(1, event.clientX / window.innerWidth * 2 - 1)), y: Math.max(-1, Math.min(1, event.clientY / window.innerHeight * 2 - 1)) } }
onMounted(() => window.addEventListener('pointermove', followPointer, { passive: true }))
onBeforeUnmount(() => window.removeEventListener('pointermove', followPointer))
</script>

<style scoped>
.robot-mascot{--scale:1;position:relative;width:230px;height:260px;isolation:isolate;cursor:pointer;user-select:none}.robot-mascot.compact{--scale:.32;width:78px;height:92px}.robot-motion{position:absolute;left:50%;bottom:20px;width:170px;height:220px;transform:translateX(calc(-50% + var(--robot-x))) rotate(var(--robot-tilt));transform-origin:50% 100%;transition:transform .65s cubic-bezier(.18,.82,.25,1);animation:robot-float 3.4s ease-in-out infinite}.robot-shadow{position:absolute;z-index:-1;left:50%;bottom:14px;width:110px;height:18px;border-radius:50%;background:rgba(5,22,34,.26);filter:blur(8px);transform:translateX(-50%);animation:robot-shadow 3.4s ease-in-out infinite}.robot-mascot.compact .robot-motion{left:50%;bottom:0;transform:translateX(calc(-50% + var(--robot-x))) rotate(var(--robot-tilt)) scale(var(--scale));transform-origin:50% 100%}.robot-mascot.compact .robot-shadow{bottom:1px;width:42px;height:7px}.robot-head{position:absolute;z-index:3;left:38px;top:14px;width:94px;height:80px;border-radius:47px 47px 42px 42px;background:radial-gradient(circle at 33% 22%,#2e3d42 0,#142127 38%,#050c10 82%);border:3px solid #49e9c6;box-shadow:0 0 0 3px rgba(104,255,224,.14),0 0 19px rgba(28,239,195,.72),inset 9px 7px 14px rgba(255,255,255,.08);overflow:hidden}.head-glass{position:absolute;inset:0;background:linear-gradient(125deg,rgba(255,255,255,.22),transparent 25%,transparent 69%,rgba(0,0,0,.32));border-radius:inherit}.robot-eyes{position:absolute;z-index:2;left:30px;top:34px;display:flex;gap:17px;transition:transform .13s linear}.robot-eyes i{display:block;width:13px;height:20px;border:2px solid #e7fffa;border-radius:8px;background:rgba(230,255,249,.08);box-shadow:0 0 7px #62ffe1, inset 0 0 5px rgba(209,255,247,.5);animation:blink 4.8s infinite}.robot-neck{position:absolute;z-index:2;left:37px;top:89px;width:96px;height:24px;border-radius:50%;background:linear-gradient(#fafefe,#c6d1d4 48%,#9ba9ad);box-shadow:0 3px 4px rgba(0,0,0,.2),inset 0 5px 6px rgba(255,255,255,.85)}.robot-body{position:absolute;left:23px;top:106px;width:124px;height:112px;border-radius:52% 52% 49% 49%;background:radial-gradient(circle at 34% 22%,#fff 0,#e6edef 34%,#cbd6d9 71%,#a9babf);box-shadow:inset -13px -15px 23px rgba(59,78,84,.19),inset 7px 8px 12px rgba(255,255,255,.75),0 14px 18px rgba(5,22,34,.16);overflow:hidden}.body-grain{position:absolute;inset:0;opacity:.36;background-image:radial-gradient(rgba(27,48,56,.28) .6px,transparent .8px);background-size:4px 4px;mix-blend-mode:multiply}.robot-ear{position:absolute;z-index:1;top:57px;width:25px;height:31px;border-radius:15px;background:linear-gradient(90deg,#b6c2c5,#fbffff 45%,#aebabe);box-shadow:0 2px 4px rgba(0,0,0,.18)}.robot-ear span{position:absolute;inset:6px;border:3px solid #fff;border-radius:50%;box-shadow:inset 0 0 0 3px #acb9bd}.ear-left{left:25px}.ear-right{right:25px}.robot-antenna{position:absolute;z-index:0;top:31px;width:2px;height:32px;background:#dce5e6;transform-origin:bottom;border-radius:3px}.robot-antenna::after{content:'';position:absolute;top:-4px;left:-2px;width:6px;height:6px;border-radius:50%;background:#ff4e82;box-shadow:0 0 5px #ff6d9a}.antenna-left{left:35px;transform:rotate(-8deg)}.antenna-right{right:35px;transform:rotate(8deg)}@keyframes robot-float{50%{translate:0 -9px}}@keyframes robot-shadow{50%{scale:.75;opacity:.4}}@keyframes blink{0%,43%,47%,100%{scale:1}45%{scale:1 .1}}
</style>
