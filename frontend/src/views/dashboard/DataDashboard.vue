<template>
  <div ref="screen" class="data-dashboard">
    <header class="screen-header">
      <div>
        <p class="eyebrow">POPULATION ANALYTICS</p>
        <h1>数据统计大屏</h1>
        <p>课程教学模拟系统，统计结果仅用于项目演示。</p>
        <small>更新时间：{{ formatDateTime(overview.generatedAt || charts.generatedAt) || '未加载' }}</small>
      </div>
      <div class="header-actions">
        <el-button :icon="Refresh" :loading="loading" @click="loadAll">刷新</el-button>
        <el-button :icon="FullScreen" @click="toggleFullscreen">全屏</el-button>
      </div>
    </header>

    <section class="metric-grid">
      <div v-if="overviewError" class="panel-error metric-error">
        <span>指标加载失败</span>
        <el-button link type="primary" :loading="overviewLoading" @click="loadOverview">重试</el-button>
      </div>
      <DashboardStatCard label="当前户籍人口" :value="overview.registeredPopulation"/>
      <DashboardStatCard label="在册流动人口" :value="overview.activeFloatingPopulation"/>
      <DashboardStatCard label="有效居住证" :value="overview.activeResidencePermits"/>
      <DashboardStatCard label="待审批" :value="overview.pendingApprovals"/>
      <DashboardStatCard label="即将到期" :value="overview.expiringResidencePermits"/>
    </section>

    <section class="chart-grid">
      <div v-if="chartsError" class="panel-error chart-error">
        <span>图表加载失败</span>
        <el-button link type="primary" :loading="chartsLoading" @click="loadCharts">重试</el-button>
      </div>
      <el-card shadow="never">
        <template #header>近 30 日迁入迁出趋势</template>
        <MigrationTrendChart :points="charts.migrationTrend"/>
      </el-card>
      <el-card shadow="never">
        <template #header>当前业务规模</template>
        <BusinessScaleChart :rows="charts.businessScale"/>
      </el-card>
      <el-card shadow="never">
        <template #header>居住证状态分布</template>
        <PermitStatusChart :rows="charts.permitStatusDistribution"/>
      </el-card>
      <el-card shadow="never">
        <template #header>行政区划户籍人口 Top 8</template>
        <RegionRankingChart :rows="charts.registeredPopulationByRegion"/>
      </el-card>
    </section>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { FullScreen, Refresh } from '@element-plus/icons-vue'
import { formatDateTime } from '../../utils/date'
import { getDashboardCharts, getDashboardOverview } from '../../api/dashboard'
import { normalizeDashboardCharts, normalizeDashboardOverview } from '../../adapters/dashboard'
import DashboardStatCard from './components/DashboardStatCard.vue'
import MigrationTrendChart from './components/MigrationTrendChart.vue'
import BusinessScaleChart from './components/BusinessScaleChart.vue'
import PermitStatusChart from './components/PermitStatusChart.vue'
import RegionRankingChart from './components/RegionRankingChart.vue'

const screen = ref(null)
const overviewLoading = ref(false)
const chartsLoading = ref(false)
const overviewError = ref(false)
const chartsError = ref(false)
const overview = reactive(normalizeDashboardOverview())
const charts = reactive(normalizeDashboardCharts())
let timer = null

const loading = computed(() => overviewLoading.value || chartsLoading.value)

async function loadOverview() {
  if (overviewLoading.value) return
  overviewLoading.value = true
  try {
    const data = await getDashboardOverview()
    Object.assign(overview, normalizeDashboardOverview(data))
    overviewError.value = false
  } catch {
    overviewError.value = true
  } finally {
    overviewLoading.value = false
  }
}

async function loadCharts() {
  if (chartsLoading.value) return
  chartsLoading.value = true
  try {
    const data = await getDashboardCharts({ days: 30, regionLimit: 8 })
    Object.assign(charts, normalizeDashboardCharts(data))
    chartsError.value = false
  } catch {
    chartsError.value = true
  } finally {
    chartsLoading.value = false
  }
}

async function loadAll() {
  if (loading.value) return
  const results = await Promise.allSettled([loadOverview(), loadCharts()])
  // allSettled 保证任一失败不抛到外层，局部状态由各自 load 函数维护
  void results
}

function schedule() {
  clearInterval(timer)
  timer = setInterval(() => {
    if (!document.hidden && !loading.value) {
      loadAll()
    }
  }, 300000)
}

function onVisibility() {
  if (!document.hidden && !loading.value) {
    loadAll()
  }
}

async function toggleFullscreen() {
  if (document.fullscreenElement) {
    await document.exitFullscreen()
  } else {
    await screen.value?.requestFullscreen?.()
  }
}

onMounted(() => {
  loadAll()
  schedule()
  document.addEventListener('visibilitychange', onVisibility)
})

onBeforeUnmount(() => {
  clearInterval(timer)
  document.removeEventListener('visibilitychange', onVisibility)
})
</script>

<style scoped>
.data-dashboard{display:flex;flex-direction:column;gap:16px;min-height:100%;padding:4px}
.screen-header{display:flex;justify-content:space-between;align-items:flex-end;padding:20px 22px;border-radius:var(--radius-large);background:linear-gradient(135deg,#0f2858,#1e40af);color:#fff}
.screen-header h1{font-size:25px;color:#fff;margin:3px 0}
.screen-header p,.screen-header small{color:#dbeafe}
.screen-header small{display:block;margin-top:8px}
.header-actions{display:flex;gap:8px}
.metric-grid{display:grid;grid-template-columns:repeat(5,minmax(0,1fr));gap:12px;position:relative}
.chart-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:16px;position:relative}
.chart-grid :deep(.el-card){border-radius:var(--radius-large)}
.panel-error{display:flex;align-items:center;justify-content:space-between;gap:12px;padding:10px 12px;border-radius:8px;background:rgba(254,226,226,.95);color:#991b1b;border:1px solid #fecaca}
.metric-error{grid-column:1 / -1}
.chart-error{grid-column:1 / -1}
@media(max-width:1200px){.metric-grid{grid-template-columns:repeat(2,minmax(0,1fr))}.chart-grid{grid-template-columns:1fr}}
@media(max-width:760px){.screen-header{align-items:flex-start;flex-direction:column;gap:14px}.metric-grid{grid-template-columns:1fr}}
</style>
