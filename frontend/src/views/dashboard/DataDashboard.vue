<template>
  <div ref="wrapperRef" class="dashboard-wrapper">
    <div ref="canvasRef" class="dashboard-canvas">
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
import { useDashboardScale } from './composables/useDashboardScale';
import { useDashboardFullscreen } from './composables/useDashboardFullscreen';
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

// 绑定缩放
useDashboardScale(wrapperRef, canvasRef, 1920, 1080);

// 绑定全屏
const { isFullscreen, toggleFullscreen } = useDashboardFullscreen();

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
  background-color: #020f28; /* 深色背景 */
  overflow: hidden;
}

.dashboard-canvas {
  position: absolute;
  top: 50%;
  left: 50%;
  width: 1920px;
  height: 1080px;
  transform-origin: center center;
  /* js 会动态写入 transform: translate(-50%, -50%) scale(xxx); */
  display: flex;
  flex-direction: column;
  color: white;
  background-image: radial-gradient(circle at center, #05193c 0%, #020f28 100%);
}

.kpi-grid {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 16px;
  padding: 16px 20px 0;
  z-index: 1;
}

.dashboard-content-skeleton {
  flex: 1;
  display: flex;
  gap: 20px;
  padding: 20px;
}

.column {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.left-column, .right-column {
  flex: 3;
}

.center-column {
  flex: 4;
}

.panel {
  flex: 1;
  /* CyberPanel 内部有自己的样式，这里只需要控制它在 flex 列中的占比即可 */
  height: calc(33.33% - 14px); /* 减去 gap 的分配 */
}

.center-top {
  flex: 1;
  position: relative;
  min-height: 400px;
}

.center-bottom {
  height: 280px;
  margin-top: 16px;
}
</style>
