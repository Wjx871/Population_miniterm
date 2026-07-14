<template>
  <div class="dashboard-page">
    <header class="hero-banner">
      <div class="hero-content">
        <p class="eyebrow">OPERATIONS DESK</p>
        <h1>工作台</h1>
        <p class="hero-description">欢迎，{{ userStore.roleLabel }}。以下均为当前授权范围内的实时聚合数据。</p>
        <div class="time-box">
          <el-icon><Clock /></el-icon> <span>更新时间：{{ formatDateTime(overview.generatedAt) || '未加载' }}</span>
        </div>
      </div>
      <div class="hero-illustration">
        <img src="/media/hero-illustration-v2.png" class="ill-img" alt="蓝色人口信息卡片三维插画" />
      </div>
      <div class="hero-actions">
        <p class="actions-title">快捷操作</p>
        <div class="action-buttons">
          <button type="button" class="action-btn" @click="$router.push('/queries/comprehensive')" v-if="can('population:view')">
            <div class="icon-wrap"><el-icon><Search /></el-icon></div>
            <span>人口综合查询</span>
          </button>
          <button type="button" class="action-btn" @click="$router.push('/statistics/dashboard')" v-if="can('statistics:view')">
            <div class="icon-wrap"><el-icon><DataAnalysis /></el-icon></div>
            <span>数据大屏</span>
          </button>
          <button type="button" class="action-btn" @click="refresh">
            <div class="icon-wrap"><el-icon :class="{'is-loading': refreshing}"><Refresh /></el-icon></div>
            <span>刷新数据</span>
          </button>
        </div>
      </div>
    </header>
    <el-alert v-if="summaryError" :title="summaryError" type="warning" :closable="false" show-icon style="margin-bottom: 16px;">
      <template #default><el-button link type="primary" @click="refresh">重试</el-button></template>
    </el-alert>
    <section class="stat-grid">
      <DashboardStatCard label="当前户籍人口" :value="overview.registeredPopulation" :icon="UserFilled" colorTheme="blue" />
      <DashboardStatCard label="在册流动人口" :value="overview.activeFloatingPopulation" :icon="UserFilled" colorTheme="teal" />
      <DashboardStatCard label="有效居住证" :value="overview.activeResidencePermits" :icon="Postcard" colorTheme="orange" />
      <DashboardStatCard v-if="can('approval:view')" label="待审批" :value="overview.pendingApprovals" :icon="Finished" colorTheme="purple" />
      <DashboardStatCard v-if="can('residence-permit:expiry:view')" label="即将到期居住证" :value="overview.expiringResidencePermits" :icon="Warning" colorTheme="red" />
      <DashboardStatCard :label="migrationInLabel" :value="overview.migrationInPeriod" :icon="TrendCharts" colorTheme="blue" />
      <DashboardStatCard :label="migrationOutLabel" :value="overview.migrationOutPeriod" :icon="TrendCharts" colorTheme="green" />
      <DashboardStatCard :label="netMigrationLabel" :value="overview.migrationInPeriod - overview.migrationOutPeriod" :icon="DataAnalysis" colorTheme="purple" />
    </section>
    <section class="content-grid"><QuickActionPanel :actions="quickActions"/><WorkItemList title="待审批事项" :items="pending.items" :error="pending.error" :retry="can('approval:view')" @retry="loadWorkItems"/><WorkItemList title="我的近期申请" :items="applications.items" :error="applications.error" :retry="can('application:view')" @retry="loadWorkItems"/><WorkItemList title="即将到期居住证" :items="expiring.items" :error="expiring.error" :retry="can('residence-permit:expiry:view')" @retry="loadWorkItems"/></section>
    <section class="chart-card"><div class="section-heading"><div><h2>近 30 日迁移趋势</h2><p>仅统计已完成迁移业务</p></div><el-button link type="primary" @click="$router.push('/statistics/dashboard')">查看数据大屏</el-button></div><MigrationTrendChart :points="charts.migrationTrend"/></section>
  </div>
</template>
<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { Refresh, UserFilled, Postcard, Finished, Warning, TrendCharts, Search, DataAnalysis, Switch, Clock } from '@element-plus/icons-vue'
import { useUserStore } from '../../stores/user'
import { PERMISSIONS } from '../../constants/permissions'
import { formatDateTime } from '../../utils/date'
import { getDashboardCharts, getDashboardOverview } from '../../api/dashboard'
import { getPendingApprovals } from '../../api/approvals'
import { getApplicationPage } from '../../api/applications'
import { getExpiringResidencePermits } from '../../api/floatingResidence'
import { normalizeDashboardCharts, normalizeDashboardOverview } from '../../adapters/dashboard'
import { getApiErrorMessage } from '../../utils/apiError'
import DashboardStatCard from './components/DashboardStatCard.vue'
import QuickActionPanel from './components/QuickActionPanel.vue'
import WorkItemList from './components/WorkItemList.vue'
import MigrationTrendChart from './components/MigrationTrendChart.vue'

const userStore=useUserStore(); const refreshing=ref(false); const summaryError=ref(''); const overview=reactive(normalizeDashboardOverview()); const charts=reactive(normalizeDashboardCharts()); const pending=reactive({items:[],error:false});const applications=reactive({items:[],error:false});const expiring=reactive({items:[],error:false})
const can=(permission)=>userStore.hasPermission(permission)
const migrationInLabel=computed(()=>`近${overview.periodDays ?? 30}日迁入`)
const migrationOutLabel=computed(()=>`近${overview.periodDays ?? 30}日迁出`)
const netMigrationLabel=computed(()=>`近${overview.periodDays ?? 30}日净流入`)
const quickActions=computed(()=>[{label:'人口综合查询',to:'/queries/comprehensive',icon:Search,permission:PERMISSIONS.POPULATION_VIEW},{label:'数据大屏',to:'/statistics/dashboard',icon:DataAnalysis,permission:PERMISSIONS.STATISTICS_VIEW},{label:'发起迁入',to:'/migrations/in/apply',icon:Switch,permission:'migration:in:create'},{label:'发起迁出',to:'/migrations/out/apply',icon:Switch,permission:'migration:out:create'}].filter(x=>can(x.permission)))
async function loadSummary(){const [overviewResult,chartsResult]=await Promise.allSettled([getDashboardOverview(),getDashboardCharts({days:30,regionLimit:8})]);const failure=[overviewResult,chartsResult].find(result=>result.status==='rejected');summaryError.value=failure?getApiErrorMessage(failure.reason,'首页统计加载失败，请稍后重试'):'';if(overviewResult.status==='fulfilled')Object.assign(overview,normalizeDashboardOverview(overviewResult.value));if(chartsResult.status==='fulfilled')Object.assign(charts,normalizeDashboardCharts(chartsResult.value))}
async function loadWorkItems(){const jobs=[];if(can('approval:view'))jobs.push(['pending',getPendingApprovals()]);if(can('application:view'))jobs.push(['applications',getApplicationPage({current:1,size:5})]);if(can('residence-permit:expiry:view'))jobs.push(['expiring',getExpiringResidencePermits({days:30})]);const results=await Promise.allSettled(jobs.map(([,job])=>job));results.forEach((result,index)=>{const key=jobs[index][0];const target={pending,applications,expiring}[key];target.error=result.status==='rejected'?getApiErrorMessage(result.reason,'加载失败，请稍后重试'):false;if(result.status!=='fulfilled')return;const data=result.value;if(key==='pending')target.items=Array.isArray(data)?data.slice(0,5).map(x=>({id:x.approvalId,title:x.title||x.applicationNo,meta:x.status||'待审批',to:`/approvals/${x.approvalId}`})):[];if(key==='applications'){const rows=Array.isArray(data?.content)?data.content:[];target.items=rows.slice(0,5).map(x=>({id:x.applicationId,title:x.title||x.applicationNo,meta:x.status||'',to:`/applications/${x.applicationId}`}))}if(key==='expiring')target.items=(Array.isArray(data)?data:[]).slice(0,5).map(x=>({id:x.permitId,title:x.personName||x.maskedPermitNo,meta:`剩余 ${x.remainingDays ?? '—'} 天`,to:`/residence-permits/${x.permitId}`}))})}
async function refresh(){refreshing.value=true;try{await Promise.all([loadSummary(),loadWorkItems()])}finally{refreshing.value=false}}
onMounted(refresh)
</script>
<style scoped>
.dashboard-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* 头部横幅 */
.hero-banner {
  display: grid;
  grid-template-columns: minmax(390px, 1fr) minmax(350px, 0.95fr) auto;
  column-gap: 24px;
  align-items: center;
  height: 230px;
  padding: 24px 36px;
  border-radius: var(--radius-large);
  background:
    radial-gradient(circle at 61% 56%, rgba(111, 169, 255, 0.14), transparent 31%),
    linear-gradient(118deg, #fbfdff 0%, #f4f8ff 50%, #f8fbff 100%);
  border: 1px solid rgba(255, 255, 255, 0.92);
  position: relative;
  overflow: hidden;
  box-shadow: 0 7px 24px rgba(32, 75, 132, 0.035);
}

.hero-content {
  flex: 1;
  position: relative;
  z-index: 2;
  transform: translateY(-36px);
}

.eyebrow {
  font-size: 12px;
  letter-spacing: .08em;
  font-weight: 700;
  color: #45628d;
  margin-bottom: 6px;
}

.hero-content h1 {
  font-size: 34px;
  line-height: 1.25;
  color: #12233e;
  margin: 0 0 8px;
  font-weight: 700;
  letter-spacing: .02em;
}

.hero-description {
  color: #243957;
  font-size: 14px;
  margin-bottom: 37px;
}

.time-box {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: #6d82a0;
  font-size: 13px;
}

/* 中间插画区域 */
.hero-illustration {
  align-self: stretch;
  display: flex;
  justify-content: center;
  align-items: center;
  position: relative;
  z-index: 1;
  min-width: 0;
  pointer-events: none;
}

.ill-img {
  width: min(540px, 42vw);
  max-width: none;
  height: 246px;
  object-fit: contain;
  opacity: .96;
  transform: translate(-8px, -31px) scaleX(1.16) scaleY(1.25);
  filter: saturate(.94) contrast(.97);
  -webkit-mask-image: linear-gradient(90deg, transparent 0%, #000 13%, #000 87%, transparent 100%);
  mask-image: linear-gradient(90deg, transparent 0%, #000 13%, #000 87%, transparent 100%);
}

/* 右侧快捷操作 */
.hero-actions {
  position: relative;
  z-index: 2;
  transform: translate(-12px, -30px);
}

.actions-title {
  font-size: 13px;
  color: #4b5563;
  margin-bottom: 16px;
  font-weight: 500;
}

.action-buttons {
  display: flex;
  gap: 18px;
}

.action-btn {
  appearance: none;
  border: 1px solid rgba(229, 235, 245, .88);
  background: white;
  border-radius: 11px;
  padding: 13px 8px 12px;
  width: 114px;
  height: 104px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  cursor: pointer;
  font-family: inherit;
  box-shadow: 0 7px 18px rgba(39, 73, 119, 0.055);
  transition: all 0.2s ease;
}

.action-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 16px rgba(22, 119, 255, 0.1);
}

.action-btn:focus-visible {
  outline: 3px solid rgba(47, 99, 226, .2);
  outline-offset: 2px;
}

.icon-wrap {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: #f0f7ff;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #1677ff;
  font-size: 20px;
}

.action-btn span {
  font-size: 13px;
  color: #4b5563;
}

.stat-grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));grid-auto-rows:115px;column-gap:20px;row-gap:18px;margin-top:3px}.content-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));grid-auto-rows:minmax(168px,auto);gap:16px;margin-top:-2px}.chart-card{padding:20px;border-radius:12px;background:#fff;border:none;box-shadow:0 4px 12px rgba(0,0,0,0.03);}.section-heading{display:flex;justify-content:space-between}.section-heading h2{font-size:15px;font-weight:600;color:#111827;}.section-heading p{font-size:12px;color:#6b7280;margin-top:4px}@media(max-width:1280px){.hero-banner{grid-template-columns:minmax(330px,1fr) minmax(280px,.8fr) auto;padding-inline:28px}.ill-img{width:430px}.action-buttons{gap:10px}.action-btn{width:94px}.stat-grid{grid-template-columns:repeat(2,minmax(0,1fr));grid-auto-rows:115px}}@media(max-width:900px){.hero-banner{display:flex;flex-direction:column;align-items:flex-start;gap:22px;height:auto;padding:26px}.hero-actions{transform:none}.hero-illustration{display:none}.action-buttons{flex-wrap:wrap}.action-btn{width:108px}.stat-grid,.content-grid{grid-template-columns:1fr}.stat-grid{grid-auto-rows:115px}}</style>
