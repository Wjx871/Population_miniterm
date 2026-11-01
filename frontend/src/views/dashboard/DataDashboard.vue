<!--
  【数据大屏链路 · 页面入口】DataDashboard.vue
  职责：布局 + 把 useDashboardData 的响应式数据传给 KPI/各面板；不直接发 HTTP、不查库。
  路由：/statistics/dashboard（见 router/routes.js）
  下游：useDashboardData → api/dashboard → request → 后端；图表侧走 adapter + dashboardChart + BaseEChart
-->
<template>
  <div
    ref="wrapperRef"
    class="dashboard-wrapper"
    :class="mode === 'scale' ? 'is-scale-mode' : 'is-scroll-mode'"
  >
    <div ref="canvasRef" class="dashboard-canvas" :class="{ 'design-canvas': mode === 'scale' }">
      <!-- 顶部：刷新 / 全屏 / 演示模式切换（事件落到 loadAll、toggleDemoMode） -->
      <DashboardScreenHeader
        :is-fullscreen="isFullscreen"
        :is-demo="isDemo"
        :loading="overviewLoading || chartsLoading"
        @refresh="loadAll"
        @toggle-fullscreen="toggleFullscreen"
        @toggle-demo="toggleDemoMode"
      />

      <!-- KPI：数据来自 overview（getDashboardOverview）；净流入为前端计算字段 -->
      <section class="kpi-grid">
        <DashboardKpiCard label="当前户籍人口" :value="overview.registeredPopulation" icon="UserFilled" tone="cyan" />
        <DashboardKpiCard label="在册流动人口" :value="overview.activeFloatingPopulation" icon="User" tone="blue" />
        <DashboardKpiCard label="有效居住证" :value="overview.activeResidencePermits" icon="Postcard" unit="张" tone="green" />
        <DashboardKpiCard label="待审批业务" :value="overview.pendingApprovals" icon="Document" unit="件" tone="yellow" />
        <DashboardKpiCard label="证件即将到期" :value="overview.expiringResidencePermits" icon="Warning" unit="张" tone="red" />
        <DashboardKpiCard label="本期净流入" :value="netMigration" icon="TrendCharts" tone="purple" />
      </section>

      <!-- 三列主内容：overview 驱动结构/重点业务；charts 驱动趋势与分布图 -->
      <main class="dashboard-content-skeleton">
        <div class="column left-column">
          <!-- populationStructure：CSS 图；migrationTrend / businessScale：ECharts 或 DOM 排行 -->
          <PopulationStructurePanel class="panel" :data="overview.populationStructure" />
          <MigrationTrendPanel class="panel" :data="charts.migrationTrend" />
          <BusinessRankingPanel class="panel" :data="charts.businessScale" />
        </div>
        <div class="column center-column">
          <div class="center-top">
            <NetworkSvgMapPanel />
          </div>
          <div class="center-bottom">
            <PopulationScalePanel :data="charts.populationScaleTrend" />
          </div>
        </div>
        <div class="column right-column">
          <ApprovalStatusPanel class="panel" :data="charts.approvalStatusDistribution" />
          <KeyBusinessMonitorPanel class="panel" :data="overview.keyBusiness" />
          <BusinessTypeSharePanel class="panel" :data="charts.businessScale" />
        </div>
      </main>

      <DashboardFooter
        :update-time="overview.generatedAt || charts.generatedAt"
        :is-demo="isDemo"
        :overview-error="overviewError"
        :charts-error="chartsError"
      />
    </div>
  </div>
</template>

<script setup>
/**
 * 页面只组合三个 composable：
 * - useDashboardData：请求与响应式数据（答辩核心）
 * - useDashboardFullscreen / useDashboardScale：展示交互，与业务数据无关
 */
import { computed, ref } from 'vue';
import { useDashboardFullscreen } from './composables/useDashboardFullscreen';
import { useDashboardScale } from './composables/useDashboardScale';
import { useDashboardData } from './composables/useDashboardData';
import DashboardScreenHeader from './components/DashboardScreenHeader.vue';
import DashboardFooter from './components/DashboardFooter.vue';
import DashboardKpiCard from './components/DashboardKpiCard.vue';

// 左列面板
import PopulationStructurePanel from './components/PopulationStructurePanel.vue';
import MigrationTrendPanel from './components/MigrationTrendPanel.vue';
import BusinessRankingPanel from './components/BusinessRankingPanel.vue';

// 右列面板
import ApprovalStatusPanel from './components/ApprovalStatusPanel.vue';
import KeyBusinessMonitorPanel from './components/KeyBusinessMonitorPanel.vue';
import BusinessTypeSharePanel from './components/BusinessTypeSharePanel.vue';

// 中列面板
import NetworkSvgMapPanel from './components/NetworkSvgMapPanel.vue';
import PopulationScalePanel from './components/PopulationScalePanel.vue';

const wrapperRef = ref(null);
const canvasRef = ref(null);

// 本期净流入 = 迁入量 - 迁出量（字段来自 overview，非后端单独接口）
const netMigration = computed(() => {
  const incoming = overview.migrationInPeriod;
  const outgoing = overview.migrationOutPeriod;
  return Number.isFinite(incoming) && Number.isFinite(outgoing) ? incoming - outgoing : null;
});

// 全屏切换
const { isFullscreen, toggleFullscreen } = useDashboardFullscreen();

// 混合缩放：全屏/大窗 scale 适配 1920×1080 设计稿，小窗 scroll
const { mode } = useDashboardScale(wrapperRef, canvasRef, {
  designWidth: 1920,
  designHeight: 1080,
  isFullscreen
});

// === 数据层：onMounted 内 loadAll；模板直接绑定 overview / charts ===
const {
  overview,
  charts,
  overviewLoading,
  chartsLoading,
  overviewError,
  chartsError,
  isDemo,
  loadAll,
  toggleDemoMode
} = useDashboardData();
</script>

<style scoped>
@import './styles/dashboard-screen.css';

.dashboard-wrapper {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background-color: var(--cyber-bg-color);
}

.dashboard-canvas {
  position: relative;
  width: 100%;
  min-width: 1400px;
  min-height: 100%;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  color: var(--cyber-text-primary);
  background-color: var(--cyber-bg-color);
  background-image:
    radial-gradient(ellipse at 50% 28%, rgba(28, 96, 180, 0.42) 0%, transparent 52%),
    radial-gradient(ellipse at 15% 80%, rgba(120, 70, 220, 0.12) 0%, transparent 35%),
    radial-gradient(ellipse at 85% 70%, rgba(40, 180, 160, 0.1) 0%, transparent 30%),
    radial-gradient(ellipse at center, var(--cyber-bg-mid) 0%, var(--cyber-bg-color) 78%);
  background-size: 100% 100%, 100% 100%, 100% 100%, 100% 100%;
  background-position: center, center, center, center;
  overflow: hidden;
}

/* 低频扫描光，增强大屏科技感 */
.dashboard-canvas::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  height: 18%;
  top: -20%;
  background: linear-gradient(
    180deg,
    transparent 0%,
    rgba(61, 240, 255, 0.035) 45%,
    transparent 100%
  );
  pointer-events: none;
  z-index: 0;
  animation: screen-scan 9s linear infinite;
}

@keyframes screen-scan {
  0% { top: -20%; opacity: 0; }
  10% { opacity: 1; }
  90% { opacity: 1; }
  100% { top: 100%; opacity: 0; }
}

.dashboard-canvas > * {
  position: relative;
  z-index: 1;
}

.kpi-grid {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 14px;
  padding: 12px 20px 0;
  z-index: 1;
  flex-shrink: 0;
}

.dashboard-content-skeleton {
  flex: 1;
  display: flex;
  gap: 16px;
  padding: 16px 20px;
  min-height: 0;
}

.column {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-height: 0;
}

.left-column, .right-column {
  flex: 4;
  min-width: 0;
}

.center-column {
  flex: 3;
  min-width: 0;
}

.panel {
  flex: 1;
  height: calc(33.33% - 11px);
  min-height: 0;
}

.center-top {
  flex: 1;
  position: relative;
  min-height: 0;
}

.center-bottom {
  height: 280px;
  flex-shrink: 0;
  margin-top: 0;
  min-height: 0;
  overflow: hidden;
}
</style>
