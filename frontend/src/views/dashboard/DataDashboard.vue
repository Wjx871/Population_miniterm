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
      />

      <!-- 顶部 KPI 指标区 -->
      <section class="kpi-grid">
        <DashboardKpiCard label="当前户籍人口" :value="overview.registeredPopulation" icon="UserFilled" />
        <DashboardKpiCard label="在册流动人口" :value="overview.activeFloatingPopulation" icon="User" />
        <DashboardKpiCard label="有效居住证" :value="overview.activeResidencePermits" icon="Postcard" unit="张" />
        <DashboardKpiCard label="待审批业务" :value="overview.pendingApprovals" icon="Document" unit="件" />
        <DashboardKpiCard label="证件即将到期" :value="overview.expiringResidencePermits" icon="Warning" unit="张" />
        <DashboardKpiCard label="本期净流入" :value="(overview.migrationInPeriod || 0) - (overview.migrationOutPeriod || 0)" icon="TrendCharts" />
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
  loadAll
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
    radial-gradient(ellipse at center, var(--cyber-bg-mid) 0%, var(--cyber-bg-color) 70%),
    linear-gradient(var(--cyber-grid) 1px, transparent 1px),
    linear-gradient(90deg, var(--cyber-grid) 1px, transparent 1px);
  background-size: 100% 100%, 48px 48px, 48px 48px;
  background-position: center, center, center;
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
  flex: 3;
  min-width: 0;
}

.center-column {
  flex: 4;
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
  height: 260px;
  flex-shrink: 0;
  margin-top: 0;
}
</style>
