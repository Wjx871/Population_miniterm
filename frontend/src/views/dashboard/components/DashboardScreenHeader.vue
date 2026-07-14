<template>
  <header class="dashboard-screen-header">
    <div class="header-left">
      <div class="header-time">{{ currentTime }}</div>
    </div>
    
    <div class="header-center">
      <h1 class="header-title">人口数据统计大屏</h1>
      <div class="header-subtitle">
        <span class="eyebrow">POPULATION ANALYTICS CENTER</span>
        <span class="demo-tag" v-if="isDemo">演示数据</span>
      </div>
    </div>
    
    <div class="header-right">
      <div class="header-actions">
        <el-button 
          class="cyber-btn"
          type="primary" 
          link 
          :icon="Back" 
          @click="$router.push('/home')"
        >
          返回系统
        </el-button>
        <el-button 
          class="cyber-btn"
          type="primary" 
          link 
          :icon="Refresh" 
          :loading="loading" 
          @click="$emit('refresh')"
        >
          刷新数据
        </el-button>
        <el-button 
          class="cyber-btn"
          type="primary" 
          link 
          :icon="isFullscreen ? Close : FullScreen" 
          @click="$emit('toggle-fullscreen')"
        >
          {{ isFullscreen ? '退出全屏' : '全屏展示' }}
        </el-button>
      </div>
    </div>
    
    <!-- 底部装饰线 -->
    <div class="header-decoration"></div>
  </header>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { Refresh, FullScreen, Close, Back } from '@element-plus/icons-vue'
import { formatDateTime } from '../../../utils/date'

defineProps({
  loading: Boolean,
  isFullscreen: Boolean,
  isDemo: Boolean
})

defineEmits(['refresh', 'toggle-fullscreen'])

const currentTime = ref(formatDateTime(new Date()))
let timer = null

onMounted(() => {
  timer = setInterval(() => {
    currentTime.value = formatDateTime(new Date())
  }, 1000)
})

onUnmounted(() => {
  clearInterval(timer)
})
</script>

<style scoped>
.dashboard-screen-header {
  height: 80px;
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  padding: 10px 30px 0;
  position: relative;
  background: url('@/assets/dashboard/header-bg.png') no-repeat center top;
  background-size: 100% 100%; /* 若无此图片，可用渐变代替 */
}

/* 渐变替代背景（如果没有图片的话） */
.dashboard-screen-header::before {
  content: '';
  position: absolute;
  top: 0; left: 0; right: 0;
  height: 60px;
  background: linear-gradient(180deg, rgba(15, 40, 88, 0.8) 0%, transparent 100%);
  pointer-events: none;
  z-index: 0;
}

.header-left, .header-right {
  flex: 1;
  display: flex;
  align-items: center;
  z-index: 1;
  margin-top: 15px;
}

.header-right {
  justify-content: flex-end;
}

.header-center {
  flex: 2;
  text-align: center;
  z-index: 1;
  position: relative;
}

.header-time {
  font-family: 'Courier New', Courier, monospace;
  font-size: 18px;
  color: var(--cyber-accent);
  letter-spacing: 2px;
  text-shadow: 0 0 10px rgba(41, 215, 255, 0.5);
}

.header-title {
  font-size: 36px;
  font-weight: 700;
  color: #fff;
  margin: 0;
  letter-spacing: 6px;
  text-shadow: 0 0 20px rgba(41, 215, 255, 0.8);
  background: linear-gradient(to bottom, #fff 0%, var(--cyber-accent) 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
}

.header-subtitle {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  margin-top: 4px;
}

.eyebrow {
  font-size: 14px;
  color: var(--cyber-text-muted);
  letter-spacing: 4px;
}

.demo-tag {
  font-size: 12px;
  color: var(--cyber-yellow);
  border: 1px solid var(--cyber-yellow);
  padding: 1px 6px;
  border-radius: 4px;
  background: rgba(252, 211, 77, 0.1);
}

.cyber-btn {
  color: var(--cyber-accent) !important;
  font-size: 15px;
  transition: all 0.3s;
}

.cyber-btn:hover {
  text-shadow: 0 0 8px var(--cyber-accent);
  transform: scale(1.05);
}

.header-decoration {
  position: absolute;
  bottom: 0;
  left: 0;
  width: 100%;
  height: 2px;
  background: linear-gradient(90deg, 
    transparent 0%, 
    rgba(41, 215, 255, 0.2) 20%, 
    var(--cyber-accent) 50%, 
    rgba(41, 215, 255, 0.2) 80%, 
    transparent 100%
  );
  box-shadow: 0 0 10px var(--cyber-accent);
}
</style>
