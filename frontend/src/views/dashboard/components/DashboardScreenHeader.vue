<template>
  <header class="dashboard-screen-header">
    <div class="header-left">
      <div class="header-time">
        <el-icon><Clock /></el-icon>
        {{ currentTime }}
      </div>
    </div>
    
    <div class="header-center">
      <div class="header-title-wrapper">
        <h1 class="header-title">人口数据库管理系统数据统计大屏</h1>
        <div class="header-subtitle">
          <span class="eyebrow">Population Data Visualization Center</span>
        </div>
      </div>
      <!-- CSS绘制的机甲底座 -->
      <div class="header-center-bg"></div>
    </div>
    
    <div class="header-right">
      <div class="header-actions">
        <el-switch
          v-model="internalDemoMode"
          class="demo-switch"
          inline-prompt
          active-text="演示"
          inactive-text="真实"
          style="--el-switch-on-color: var(--cyber-yellow); --el-switch-off-color: var(--cyber-accent); margin-right: 12px;"
          @change="$emit('toggle-demo', $event)"
        />
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
import { ref, watch, onMounted, onUnmounted } from 'vue'
import { Refresh, FullScreen, Close, Back, Clock } from '@element-plus/icons-vue'
import { formatDateTime } from '../../../utils/date'

const props = defineProps({
  loading: Boolean,
  isFullscreen: Boolean,
  isDemo: Boolean
})

defineEmits(['refresh', 'toggle-fullscreen', 'toggle-demo'])

const internalDemoMode = ref(props.isDemo)
watch(() => props.isDemo, (newVal) => {
  internalDemoMode.value = newVal
})

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
  height: 90px;
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  padding: 0 30px;
  position: relative;
  /* 移除原本简单的背景图，改用CSS直接绘制机甲效果 */
}

/* 顶部全屏横线发光 */
.dashboard-screen-header::after {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 2px;
  background: linear-gradient(90deg, 
    transparent 0%, 
    var(--cyber-accent) 20%, 
    #fff 50%, 
    var(--cyber-accent) 80%, 
    transparent 100%
  );
  box-shadow: 0 0 15px var(--cyber-accent);
}

.header-left, .header-right {
  flex: 1;
  display: flex;
  align-items: center;
  z-index: 2;
  margin-top: 25px;
}

.header-right {
  justify-content: flex-end;
}

.header-center {
  flex: 2;
  display: flex;
  flex-direction: column;
  align-items: center;
  position: relative;
  z-index: 1;
  height: 100%;
}

/* 机甲切角底座 (向下凸出的梯形底托) */
.header-center-bg {
  position: absolute;
  top: 0;
  width: 100%;
  max-width: 800px;
  height: 80px;
  background: rgba(10, 30, 80, 0.4);
  backdrop-filter: blur(4px);
  /* 使用切割生成向下梯形 */
  clip-path: polygon(0 0, 100% 0, 95% 100%, 5% 100%);
  border-bottom: 2px solid var(--cyber-accent);
  box-shadow: inset 0 -10px 20px rgba(0, 229, 255, 0.2);
  z-index: -1;
}

/* 底座边框的高亮发光线 */
.header-center-bg::before {
  content: '';
  position: absolute;
  bottom: 0;
  left: 5%;
  right: 5%;
  height: 2px;
  background: var(--cyber-accent);
  box-shadow: 0 0 15px var(--cyber-accent-glow);
}

.header-title-wrapper {
  margin-top: 15px;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.header-time {
  font-family: 'Courier New', Courier, monospace;
  font-size: 16px;
  color: #e6f6ff;
  display: flex;
  align-items: center;
  gap: 8px;
  letter-spacing: 1px;
  text-shadow: 0 0 8px rgba(61, 240, 255, 0.35);
}

.header-title {
  font-size: 32px;
  font-weight: 800;
  color: #ffffff;
  margin: 0;
  letter-spacing: 6px;
  /* 不用 transparent fill，避免某些环境下标题发虚/看不见 */
  text-shadow:
    0 0 12px rgba(61, 240, 255, 0.85),
    0 2px 10px rgba(0, 0, 0, 0.45);
}

.header-subtitle {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  margin-top: 4px;
}

.eyebrow {
  font-size: 13px;
  color: #c5e4ff;
  letter-spacing: 2px;
  text-transform: uppercase;
}

.demo-tag {
  font-size: 12px;
  color: var(--cyber-yellow);
  border: 1px solid var(--cyber-yellow);
  padding: 1px 6px;
  border-radius: 2px;
  background: rgba(252, 211, 77, 0.1);
  box-shadow: 0 0 8px rgba(252, 211, 77, 0.2);
}

.cyber-btn {
  color: #f2fbff !important;
  font-size: 14px;
  min-height: 32px;
  padding: 4px 10px !important;
  border: 1px solid rgba(77, 240, 255, 0.35) !important;
  border-radius: 2px;
  transition: color 0.25s, border-color 0.25s, text-shadow 0.25s, box-shadow 0.25s;
  margin-left: 8px;
}

.cyber-btn:hover {
  color: #7cffff !important;
  border-color: rgba(77, 240, 255, 0.75) !important;
  text-shadow: 0 0 8px var(--cyber-accent);
  box-shadow: 0 0 12px rgba(61, 240, 255, 0.25);
}

.header-decoration {
  display: none;
}
</style>
