<template>
  <div class="query-page">
    <div class="page-header">
      <div>
        <p class="eyebrow">QUERY WORKBENCH</p>
        <h1>人口综合查询</h1>
        <p class="subtitle">查询结果严格受当前账户数据范围限制，列表字段默认脱敏。</p>
      </div>
      <el-button :icon="Refresh" plain @click="fetchPage">刷新</el-button>
    </div>
    <SearchPanel @search="search" @reset="reset">
      <el-form :inline="true" :model="query">
        <el-form-item label="综合关键词">
          <el-input v-model="query.keyword" clearable placeholder="姓名、身份证号、户号或居住证号"/>
        </el-form-item>
        <el-form-item label="人员状态">
          <el-input v-model="query.personStatus" clearable placeholder="如 REGISTERED"/>
        </el-form-item>
        <el-form-item label="行政区划">
          <RegionCascader v-model="query.regionCode" placeholder="选择区划" style="width: 160px" />
        </el-form-item>
        <el-form-item label="户籍状态">
          <el-select v-model="query.residenceStatus" clearable placeholder="全部当前记录" style="width: 160px">
            <el-option label="全部当前记录" value=""/>
            <el-option label="当前有效 ACTIVE" value="ACTIVE"/>
          </el-select>
        </el-form-item>
        <el-form-item label="流动登记">
          <el-select v-model="query.floatingStatus" clearable placeholder="全部当前记录" style="width: 160px">
            <el-option label="全部当前记录" value=""/>
            <el-option label="当前有效 ACTIVE" value="ACTIVE"/>
          </el-select>
        </el-form-item>
        <el-form-item label="居住证状态">
          <el-select v-model="query.permitStatus" clearable placeholder="全部当前记录" style="width: 160px">
            <el-option label="全部当前记录" value=""/>
            <el-option label="当前有效 ACTIVE" value="ACTIVE"/>
          </el-select>
        </el-form-item>
      </el-form>
    </SearchPanel>
    <el-card shadow="never" class="result-card">
      <ComprehensiveResultTable :records="records" :loading="loading" @profile="openProfile"/>
      <AppPagination v-model:current="query.current" v-model:size="query.size" :total="total" @change="fetchPage"/>
    </el-card>
    <ComprehensiveProfileDrawer v-model:visible="drawerVisible" :loading="profileLoading" :profile="profile"/>
  </div>
</template>
<script setup>
import { onMounted, reactive, ref } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import SearchPanel from '../../components/common/SearchPanel.vue'
import AppPagination from '../../components/common/AppPagination.vue'
import RegionCascader from '../../components/business/RegionCascader.vue'
import ComprehensiveResultTable from './components/ComprehensiveResultTable.vue'
import ComprehensiveProfileDrawer from './components/ComprehensiveProfileDrawer.vue'
import { getComprehensivePersonPage, getComprehensivePersonProfile } from '../../api/comprehensiveQuery'
import { normalizeComprehensivePage, normalizeComprehensiveProfile } from '../../adapters/comprehensiveQuery'

const query = reactive({
  keyword: '',
  personStatus: '',
  regionCode: '',
  residenceStatus: '',
  floatingStatus: '',
  permitStatus: '',
  current: 1,
  size: 10
})
const records = ref([])
const total = ref(0)
const loading = ref(false)
const drawerVisible = ref(false)
const profileLoading = ref(false)
const profile = ref(null)

async function fetchPage() {
  loading.value = true
  try {
    const page = normalizeComprehensivePage(await getComprehensivePersonPage(query))
    records.value = page.records
    total.value = page.total
  } catch {
    records.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function search() {
  query.current = 1
  fetchPage()
}

function reset() {
  Object.assign(query, {
    keyword: '',
    personStatus: '',
    regionCode: '',
    residenceStatus: '',
    floatingStatus: '',
    permitStatus: '',
    current: 1
  })
  fetchPage()
}

async function openProfile(row) {
  drawerVisible.value = true
  profileLoading.value = true
  profile.value = null
  try {
    profile.value = normalizeComprehensiveProfile(await getComprehensivePersonProfile(row.personId))
  } finally {
    profileLoading.value = false
  }
}

onMounted(fetchPage)
</script>
<style scoped>
.query-page{display:flex;flex-direction:column;gap:16px}
.page-header{display:flex;justify-content:space-between;align-items:flex-end}
.eyebrow{font-size:11px;font-weight:700;letter-spacing:.12em;color:var(--color-accent)}
h1{font-size:24px;margin:2px 0 6px}
.subtitle{color:var(--color-ink-muted);font-size:14px}
.result-card{border-radius:var(--radius-large)}
@media(max-width:760px){.page-header{align-items:flex-start;gap:12px;flex-direction:column}}
</style>
