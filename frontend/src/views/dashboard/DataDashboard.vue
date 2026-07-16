<template>
  <div
    ref="wrapperRef"
    class="dashboard-wrapper"
    :class="mode === 'scale' ? 'is-scale-mode' : 'is-scroll-mode'"
  >
    <div ref="canvasRef" class="dashboard-canvas" :class="{ 'design-canvas': mode === 'scale' }">
      <!-- 顶部 Header -->
      <DashboardScreenHeader
        :is-fullscreen="isFullscreen"
        :is-demo="isDemo"
        :loading="overviewLoading || chartsLoading"
        @refresh="loadAll"
        @toggle-fullscreen="toggleFullscreen"
        @toggle-demo="toggleDemoMode"
      />

      <!-- 顶部 KPI 指标区 -->
      <section class="kpi-grid">
        <DashboardKpiCard label="当前户籍人口" :value="overview.registeredPopulation" icon="UserFilled" tone="cyan" />
        <DashboardKpiCard label="在册流动人口" :value="overview.activeFloatingPopulation" icon="User" tone="blue" />
        <DashboardKpiCard label="有效居住证" :value="overview.activeResidencePermits" icon="Postcard" unit="张" tone="green" />
        <DashboardKpiCard label="待审批业务" :value="overview.pendingApprovals" icon="Document" unit="件" tone="yellow" />
        <DashboardKpiCard label="证件即将到期" :value="overview.expiringResidencePermits" icon="Warning" unit="张" tone="red" />
        <DashboardKpiCard label="本期净流入" :value="(overview.migrationInPeriod || 0) - (overview.migrationOutPeriod || 0)" icon="TrendCharts" tone="purple" />
      </section>

      <!-- 主要内容区骨架 -->
      <main class="dashboard-content-skeleton">
        <div class="column left-column">
          <PopulationStructurePanel class="panel" :data="overview.populationStructure" />
          <MigrationTrendPanel class="panel" :data="charts.migrationTrend" />
          <BusinessRankingPanel class="panel" :data="charts.businessScale" />
        </div>
        <div class="column center-column">
          <div class="center-top">
            <NetworkSvgMapPanel :data="charts.registeredPopulationByRegion" />
          </div>
          <div class="center-bottom">
            <PopulationScalePanel :data="charts.populationScaleTrend" />
          </div>
        </div>
        <div class="column right-column">
          <ApprovalStatusPanel class="panel" :data="charts.permitStatusDistribution" />
          <KeyBusinessMonitorPanel class="panel" :data="overview.keyBusiness" />
          <BusinessTypeSharePanel class="panel" :data="charts.businessScale" />
        </div>
      </main>

      <DashboardFooter
        :update-time="overview.generatedAt || charts.generatedAt"
        :overview-error="overviewError"
        :charts-error="chartsError"
      />
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue';
import { useDashboardFullscreen } from './composables/useDashboardFullscreen';
import { useDashboardScale } from './composables/useDashboardScale';
import { useDashboardData } from './composables/useDashboardData';
import DashboardScreenHeader from './components/DashboardScreenHeader.vue';
import DashboardFooter from './components/DashboardFooter.vue';
import DashboardKpiCard from './components/DashboardKpiCard.vue';

// Left panels
import PopulationStructurePanel from './components/PopulationStructurePanel.vue';
import MigrationTrendPanel from './components/MigrationTrendPanel.vue';
import BusinessRankingPanel from './components/BusinessRankingPanel.vue';

// Right panels
import ApprovalStatusPanel from './components/ApprovalStatusPanel.vue';
import KeyBusinessMonitorPanel from './components/KeyBusinessMonitorPanel.vue';
import BusinessTypeSharePanel from './components/BusinessTypeSharePanel.vue';

// Center panels
import NetworkSvgMapPanel from './components/NetworkSvgMapPanel.vue';
import PopulationScalePanel from './components/PopulationScalePanel.vue';

const wrapperRef = ref(null);
const canvasRef = ref(null);

// 绑定全屏
const { isFullscreen, toggleFullscreen } = useDashboardFullscreen();

// 混合缩放：全屏/大窗 scale，小窗 scroll
const { mode } = useDashboardScale(wrapperRef, canvasRef, {
  designWidth: 1920,
  designHeight: 1080,
  isFullscreen
});

// 绑定数据层
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
