<template>
  <div class="dashboard-page">
    <header class="hero-banner">
      <div class="hero-content">
        <p class="eyebrow">OPERATIONS DESK</p>
        <h1>工作台</h1>
        <p>欢迎，{{ userStore.roleLabel }}。以下均为当前授权范围内的实时聚合数据。</p>
        <div class="time-box">
          <el-icon><Clock /></el-icon> <span>更新时间：{{ formatDateTime(overview.generatedAt) || '未加载' }}</span>
        </div>
      </div>
      <div class="hero-illustration">
        <!-- SVG Mocking the ID card design in the screenshot -->
        <svg width="220" height="120" viewBox="0 0 220 120" fill="none" xmlns="http://www.w3.org/2000/svg" class="ill-svg">
          <rect x="20" y="20" width="160" height="90" rx="8" fill="url(#paint0_linear)" opacity="0.9" />
          <rect x="40" y="45" width="40" height="40" rx="20" fill="white" opacity="0.3" />
          <circle cx="60" cy="55" r="8" fill="white" />
          <path d="M48 70C48 64 54 62 60 62C66 62 72 64 72 70" stroke="white" stroke-width="3" stroke-linecap="round"/>
          <rect x="95" y="45" width="60" height="6" rx="3" fill="white" opacity="0.8" />
          <rect x="95" y="60" width="60" height="6" rx="3" fill="white" opacity="0.8" />
          <rect x="95" y="75" width="40" height="6" rx="3" fill="white" opacity="0.5" />
          <defs>
            <linearGradient id="paint0_linear" x1="20" y1="20" x2="180" y2="110" gradientUnits="userSpaceOnUse">
              <stop stop-color="#409eff" />
              <stop offset="1" stop-color="#1677ff" />
            </linearGradient>
          </defs>
        </svg>
      </div>
      <div class="hero-actions">
        <p class="actions-title">快捷操作</p>
        <div class="action-buttons">
          <div class="action-btn" @click="$router.push('/queries/comprehensive')" v-if="can('population:view')">
            <div class="icon-wrap"><el-icon><Search /></el-icon></div>
            <span>人口综合查询</span>
          </div>
          <div class="action-btn" @click="$router.push('/statistics/dashboard')" v-if="can('statistics:view')">
            <div class="icon-wrap"><el-icon><DataAnalysis /></el-icon></div>
            <span>数据大屏</span>
          </div>
          <div class="action-btn" @click="refresh">
            <div class="icon-wrap"><el-icon :class="{'is-loading': refreshing}"><Refresh /></el-icon></div>
            <span>刷新数据</span>
          </div>
        </div>
      </div>
    </header>
    <el-alert v-if="summaryError" :title="summaryError" type="warning" :closable="false" show-icon style="margin-bottom: 16px;">
      <template #default><el-button link type="primary" @click="refresh">重试</el-button></template>
    </el-alert>
    <section class="stat-grid">
      <DashboardStatCard label="当前户籍人口" :value="overview.registeredPopulation" :icon="User" colorTheme="blue" />
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
import { Refresh, User, UserFilled, Postcard, Finished, Warning, TrendCharts, Search, DataAnalysis, Switch, Clock } from '@element-plus/icons-vue'
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
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 24px 32px;
  border-radius: var(--radius-large);
  background: linear-gradient(135deg, #f0f7ff 0%, #e6f3ff 100%);
  position: relative;
  overflow: hidden;
  box-shadow: 0 1px 3px rgba(0,0,0,0.02);
}

.hero-content {
  flex: 1;
  position: relative;
  z-index: 2;
}

.eyebrow {
  font-size: 11px;
  letter-spacing: .14em;
  font-weight: 700;
  color: #1677ff;
  margin-bottom: 8px;
}

.hero-content h1 {
  font-size: 28px;
  color: #1f2937;
  margin: 0 0 12px;
  font-weight: 600;
}

.hero-content p {
  color: #4b5563;
  font-size: 14px;
  margin-bottom: 20px;
}

.time-box {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: #8c8c8c;
  font-size: 13px;
}

/* 中间插画区域 */
.hero-illustration {
  flex: 1;
  display: flex;
  justify-content: center;
  position: relative;
  z-index: 1;
  opacity: 0.8;
}

.ill-svg {
  filter: drop-shadow(0 10px 20px rgba(22, 119, 255, 0.2));
  transform: rotate(-5deg) scale(1.1);
}

/* 右侧快捷操作 */
.hero-actions {
  position: relative;
  z-index: 2;
  margin-left: 30px;
}

.actions-title {
  font-size: 13px;
  color: #4b5563;
  margin-bottom: 12px;
  font-weight: 500;
}

.action-buttons {
  display: flex;
  gap: 12px;
}

.action-btn {
  background: white;
  border-radius: 12px;
  padding: 16px 12px;
  width: 96px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  cursor: pointer;
  box-shadow: 0 4px 12px rgba(0,0,0,0.04);
  transition: all 0.2s ease;
}

.action-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 16px rgba(22, 119, 255, 0.1);
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

.stat-grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:16px}.content-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:16px}.chart-card{padding:20px;border-radius:12px;background:#fff;border:none;box-shadow:0 4px 12px rgba(0,0,0,0.03);}.section-heading{display:flex;justify-content:space-between}.section-heading h2{font-size:15px;font-weight:600;color:#111827;}.section-heading p{font-size:12px;color:#6b7280;margin-top:4px}@media(max-width:1200px){.stat-grid{grid-template-columns:repeat(2,minmax(0,1fr))}}@media(max-width:900px){.hero-banner{flex-direction:column;align-items:flex-start;gap:24px}.hero-illustration{display:none}.action-buttons{flex-wrap:wrap}.stat-grid,.content-grid{grid-template-columns:1fr}}</style>
