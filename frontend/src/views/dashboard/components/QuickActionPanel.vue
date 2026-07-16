<template>
  <el-card shadow="never" class="panel">
    <template #header>
      <div class="header">
        <span class="title">常用入口</span>
        <span class="header-hint">悬停展开</span>
      </div>
    </template>

    <div class="actions" aria-label="常用入口">
      <button
        v-for="(item, index) in actions"
        :key="item.to"
        type="button"
        class="action-item"
        :class="`action-${index + 1}`"
        @click="router.push(item.to)"
      >
        <span class="card-wash" aria-hidden="true" />
        <span class="card-top">
          <span class="icon-wrap"><el-icon><component :is="item.icon" /></el-icon></span>
          <small>QUICK {{ String(index + 1).padStart(2, '0') }}</small>
        </span>
        <strong>{{ item.label }}</strong>
        <span class="card-description">{{ getDescription(item.label) }}</span>
        <span class="card-arrow" aria-hidden="true">→</span>
      </button>
    </div>
  </el-card>
</template>

<script setup>
import { useRouter } from 'vue-router'

defineProps({
  actions: { type: Array, default: () => [] }
})

const router = useRouter()

const descriptions = {
  '人口综合查询': '快速检索人口档案',
  '数据大屏': '查看实时数据总览',
  '发起迁入': '在线提交迁入申请',
  '发起迁出': '在线提交迁出申请'
}

function getDescription(label) {
  return descriptions[label] || '进入相关政务服务'
}
</script>

<style scoped>
.panel {
  overflow: hidden;
  border: none;
  border-radius: 14px;
  box-shadow: 0 5px 18px rgba(26, 57, 99, .055);
}

.header { display: flex; align-items: baseline; gap: 9px; }
.title { font-size: 15px; font-weight: 700; color: #111f37; }
.header-hint { font-size: 12px; color: #8a9ab1; }

:deep(.el-card__header) { padding: 16px 20px 11px; border-bottom: 1px solid #eef2f7; }
:deep(.el-card__body) { min-height: 274px; padding: 0; }

.actions {
  display: grid;
  min-height: 274px;
  padding: 17px 18px 14px;
  overflow: hidden;
  isolation: isolate;
  place-items: center;
  grid-template-areas: 'stack';
  background: linear-gradient(135deg, #fbfdff, #f5f8fd);
}

.action-item {
  position: relative;
  grid-area: stack;
  display: flex;
  width: min(305px, 76%);
  height: 122px;
  flex-direction: column;
  align-items: flex-start;
  overflow: hidden;
  border: 2px solid rgba(220, 228, 240, .9);
  border-radius: 14px;
  padding: 14px 17px;
  color: #1f3d63;
  background: rgba(248, 251, 255, .82);
  box-shadow: 0 10px 23px rgba(45, 75, 117, .1);
  cursor: pointer;
  font-family: inherit;
  text-align: left;
  transform-origin: center;
  transition: transform .7s cubic-bezier(.2,.8,.2,1), filter .7s ease, box-shadow .45s ease, border-color .4s ease, background .4s ease;
}

.action-item::after { position: absolute; top: -42px; right: -34px; width: 126px; height: 126px; border-radius: 50%; background: currentColor; content: ''; opacity: .07; }
.action-1 { z-index: 1; filter: grayscale(1); transform: translate(-42px, -31px) skewY(-8deg); }
.action-2 { z-index: 2; filter: grayscale(1); transform: translate(-13px, -11px) skewY(-8deg); }
.action-3 { z-index: 3; transform: translate(17px, 10px) skewY(-8deg); }
.action-4 { z-index: 4; transform: translate(46px, 31px) skewY(-8deg); }

.action-item:hover { z-index: 8; filter: grayscale(0); border-color: rgba(255,255,255,.98); background: rgba(255,255,255,.94); box-shadow: 0 19px 34px rgba(31, 66, 114, .22); }
.action-1:hover { transform: translate(-42px, -62px) skewY(-5deg); }
.action-2:hover { transform: translate(-13px, -45px) skewY(-5deg); }
.action-3:hover { transform: translate(17px, -20px) skewY(-5deg); }
.action-4:hover { transform: translate(46px, 1px) skewY(-5deg); }
.action-item:focus-visible { outline: 3px solid rgba(53, 119, 246, .35); outline-offset: 3px; }

.card-top { position: relative; z-index: 1; display: flex; width: 100%; align-items: center; gap: 8px; }
.icon-wrap { display: grid; width: 29px; height: 29px; place-items: center; border-radius: 9px; background: rgba(255,255,255,.84); box-shadow: inset 0 0 0 1px rgba(255,255,255,.8); font-size: 16px; }
.card-top small { font-size: 10px; font-weight: 700; letter-spacing: .12em; opacity: .65; }
.action-item strong { position: relative; z-index: 1; margin-top: 10px; font-size: 17px; line-height: 1.08; }
.card-description { position: relative; z-index: 1; margin-top: 5px; color: #7085a2; font-size: 12px; }
.card-arrow { position: absolute; z-index: 1; right: 16px; bottom: 11px; font-size: 19px; transition: transform .3s ease; }
.action-item:hover .card-arrow { transform: translateX(6px); }
.card-wash { position: absolute; right: -30px; bottom: -48px; width: 116px; height: 116px; border-radius: 50%; background: currentColor; filter: blur(8px); opacity: .1; }

@media (max-width: 900px) {
  .action-item { width: min(310px, 78%); }
}
</style>
