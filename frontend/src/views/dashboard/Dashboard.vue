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
        <p class="actions-title">常用入口 <span>悬停展开</span></p>
        <div class="action-card-stack" aria-label="常用入口">
          <button v-for="(action, index) in heroActions" :key="action.label" type="button" class="action-card" :class="[`card-${index + 1}`, action.tone]" @click="runHeroAction(action)">
            <span class="card-glow"></span>
            <span class="card-top"><span class="icon-wrap"><el-icon :class="{ 'is-loading': action.refresh && refreshing }"><component :is="action.icon" /></el-icon></span><small>{{ action.kicker }}</small></span>
            <strong>{{ action.label }}</strong>
            <span class="card-description">{{ action.description }}</span>
            <span class="card-arrow">→</span>
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
      <DashboardStatCard :label="netMigrationLabel" :value="netMigration" :icon="DataAnalysis" colorTheme="purple" />
    </section>
    <section class="content-grid"><QuickActionPanel :actions="quickActions"/><WorkItemList title="待审批事项" :items="pending.items" :error="pending.error" :retry="can('approval:view')" @retry="loadWorkItems"/><WorkItemList title="我的近期申请" :items="applications.items" :error="applications.error" :retry="can('application:view')" @retry="loadWorkItems"/><WorkItemList title="即将到期居住证" :items="expiring.items" :error="expiring.error" :retry="can('residence-permit:expiry:view')" @retry="loadWorkItems"/></section>
    <section class="chart-card"><div class="section-heading"><div><h2>近 30 日迁移趋势</h2><p>仅统计已完成迁移业务</p></div><el-button link type="primary" @click="$router.push('/statistics/dashboard')">查看数据大屏</el-button></div><MigrationTrendChart :points="charts.migrationTrend"/></section>
  </div>
</template>
<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
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

const router=useRouter(); const userStore=useUserStore(); const refreshing=ref(false); const summaryError=ref(''); const overview=reactive(normalizeDashboardOverview()); const charts=reactive(normalizeDashboardCharts()); const pending=reactive({items:[],error:false});const applications=reactive({items:[],error:false});const expiring=reactive({items:[],error:false})
const can=(permission)=>userStore.hasPermission(permission)
const netMigration=computed(()=>Number.isFinite(overview.migrationInPeriod)&&Number.isFinite(overview.migrationOutPeriod)?overview.migrationInPeriod-overview.migrationOutPeriod:null)
const migrationInLabel=computed(()=>`近${overview.periodDays ?? 30}日迁入`)
const migrationOutLabel=computed(()=>`近${overview.periodDays ?? 30}日迁出`)
const netMigrationLabel=computed(()=>`近${overview.periodDays ?? 30}日净流入`)
const quickActions=computed(()=>[{label:'人口综合查询',to:'/queries/comprehensive',icon:Search,permission:PERMISSIONS.POPULATION_VIEW},{label:'数据大屏',to:'/statistics/dashboard',icon:DataAnalysis,permission:PERMISSIONS.STATISTICS_VIEW},{label:'发起迁入',to:'/migrations/in/apply',icon:Switch,permission:'migration:in:create'},{label:'发起迁出',to:'/migrations/out/apply',icon:Switch,permission:'migration:out:create'}].filter(x=>can(x.permission)))
const heroActions=computed(()=>[
  { label:'人口综合查询', kicker:'QUERY', description:'快速检索人口档案', icon:Search, to:'/queries/comprehensive', tone:'blue', permission:PERMISSIONS.POPULATION_VIEW },
  { label:'数据大屏', kicker:'INSIGHTS', description:'查看实时数据总览', icon:DataAnalysis, to:'/statistics/dashboard', tone:'teal', permission:PERMISSIONS.STATISTICS_VIEW },
  { label:'刷新数据', kicker:'REFRESH', description:'同步当前工作台数据', icon:Refresh, tone:'violet', refresh:true },
].filter(action=>!action.permission || can(action.permission)))
function runHeroAction(action){ if(action.refresh) return refresh(); if(action.to) router.push(action.to) }
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

/* 右侧常用入口：层叠卡牌，鼠标悬停时浮起并恢复强调色。 */
.hero-actions {
  position: relative;
  z-index: 2;
  width: 306px;
  transform: translate(-2px, -8px);
}

.actions-title {
  font-size: 13px;
  color: #4b5563;
  margin: 0 0 12px 8px;
  font-weight: 500;
}
.actions-title span{font-size:11px;font-weight:400;color:#8b9bb2;margin-left:6px}.action-card-stack{position:relative;width:282px;height:145px;isolation:isolate}.action-card{position:absolute;left:22px;top:8px;width:236px;height:122px;overflow:hidden;display:flex;flex-direction:column;align-items:flex-start;border:1px solid rgba(213,224,239,.9);border-radius:15px;padding:13px 16px;background:rgba(248,251,255,.9);color:#18314d;text-align:left;font-family:inherit;cursor:pointer;box-shadow:0 10px 24px rgba(47,78,120,.11);transition:transform .5s cubic-bezier(.2,.8,.2,1),filter .5s ease,box-shadow .5s ease,border-color .35s ease;transform-origin:center bottom}.action-card::after{content:'';position:absolute;right:-35px;top:-40px;width:130px;height:130px;border-radius:50%;background:currentColor;opacity:.07}.card-1{z-index:1;transform:translate(-24px,-12px) rotate(-7deg);filter:grayscale(.85) saturate(.6)}.card-2{z-index:2;transform:translate(1px,2px) rotate(-3deg);filter:grayscale(.55) saturate(.78)}.card-3{z-index:3;transform:translate(27px,16px) rotate(1deg)}.action-card:hover{z-index:5;filter:none;border-color:rgba(255,255,255,.95);box-shadow:0 18px 32px rgba(32,69,119,.21)}.card-1:hover{transform:translate(-24px,-39px) rotate(-4deg)}.card-2:hover{transform:translate(1px,-25px) rotate(-1deg)}.card-3:hover{transform:translate(27px,-9px) rotate(2deg)}.action-card:focus-visible{outline:3px solid rgba(47,99,226,.32);outline-offset:3px}.card-top{position:relative;z-index:1;display:flex;align-items:center;gap:8px;width:100%}.icon-wrap{display:grid;place-items:center;width:29px;height:29px;border-radius:9px;background:rgba(255,255,255,.78);font-size:16px;box-shadow:inset 0 0 0 1px rgba(255,255,255,.7)}.card-top small{font-size:10px;letter-spacing:.12em;font-weight:700;opacity:.68}.action-card strong{position:relative;z-index:1;margin-top:10px;font-size:17px;line-height:1.1}.card-description{position:relative;z-index:1;margin-top:5px;color:#66809d;font-size:12px}.card-arrow{position:absolute;right:15px;bottom:12px;font-size:19px;transition:transform .25s}.action-card:hover .card-arrow{transform:translateX(5px)}.card-glow{position:absolute;inset:auto -34px -50px auto;width:118px;height:118px;border-radius:50%;background:currentColor;opacity:.1;filter:blur(7px)}.blue{color:#2869cb;background:linear-gradient(125deg,#eff6ff,#f9fcff)}.teal{color:#147d78;background:linear-gradient(125deg,#e8faf6,#f9fffd)}.violet{color:#6a55c9;background:linear-gradient(125deg,#f2efff,#fbfaff)}

.stat-grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));grid-auto-rows:115px;column-gap:20px;row-gap:18px;margin-top:3px}.content-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));grid-auto-rows:minmax(168px,auto);gap:16px;margin-top:-2px}.chart-card{padding:20px;border-radius:12px;background:#fff;border:none;box-shadow:0 4px 12px rgba(0,0,0,0.03);}.section-heading{display:flex;justify-content:space-between}.section-heading h2{font-size:15px;font-weight:600;color:#111827;}.section-heading p{font-size:12px;color:#6b7280;margin-top:4px}@media(max-width:1280px){.hero-banner{grid-template-columns:minmax(330px,1fr) minmax(280px,.8fr) auto;padding-inline:28px}.ill-img{width:430px}.action-buttons{gap:10px}.action-btn{width:94px}.stat-grid{grid-template-columns:repeat(2,minmax(0,1fr));grid-auto-rows:115px}}@media(max-width:900px){.hero-banner{display:flex;flex-direction:column;align-items:flex-start;gap:22px;height:auto;padding:26px}.hero-actions{transform:none}.hero-illustration{display:none}.action-buttons{flex-wrap:wrap}.action-btn{width:108px}.stat-grid,.content-grid{grid-template-columns:1fr}.stat-grid{grid-auto-rows:115px}}</style>
