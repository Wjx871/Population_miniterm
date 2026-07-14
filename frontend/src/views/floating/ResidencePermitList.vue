<template>
  <div class="page-container" v-loading="loading">
    <div class="page-header">
      <div><h1>居住证管理</h1><p class="subtitle">课程模拟系统，所有证件信息仅用于演示。</p></div>
      <div class="header-actions">
        <el-button v-if="canApply" type="primary" @click="openCreate">新增居住证</el-button>
      </div>
    </div>

    <el-tabs v-model="activeTab" @tab-change="onTabChange">
      <el-tab-pane label="居住证列表" name="list">
        <SearchPanel @search="handleSearch" @reset="handleReset">
          <el-form :model="query" inline>
            <el-form-item label="证件编号"><el-input v-model="query.permitNo" clearable placeholder="证件编号" /></el-form-item>
            <el-form-item label="姓名"><el-input v-model="query.personName" clearable placeholder="姓名" /></el-form-item>
            <el-form-item label="身份证号"><el-input v-model="query.identityNo" clearable placeholder="身份证号" /></el-form-item>
            <el-form-item label="签发区域"><el-input v-model="query.currentRegionCode" clearable placeholder="区划编码" /></el-form-item>
            <el-form-item label="状态">
              <el-select v-model="query.status" clearable placeholder="全部" style="width: 140px">
                <el-option v-for="(label, code) in RESIDENCE_PERMIT_STATUS" :key="code" :label="label" :value="code" />
              </el-select>
            </el-form-item>
            <el-form-item label="有效期起"><el-date-picker v-model="query.validFrom" type="date" value-format="YYYY-MM-DD" placeholder="起始" clearable /></el-form-item>
            <el-form-item label="有效期止"><el-date-picker v-model="query.validTo" type="date" value-format="YYYY-MM-DD" placeholder="截止" clearable /></el-form-item>
          </el-form>
        </SearchPanel>

        <el-card shadow="never">
          <el-table :data="records" stripe style="width:100%">
            <el-table-column label="证件编号" min-width="150"><template #default="{row}"><SensitiveText :value="row.permitNo" kind="text" /></template></el-table-column>
            <el-table-column prop="personName" label="姓名" min-width="100" />
            <el-table-column label="身份证号" min-width="170"><template #default="{row}"><SensitiveText :value="row.identityNo" kind="idCard" /></template></el-table-column>
            <el-table-column label="当前地址" min-width="160" show-overflow-tooltip><template #default="{row}">{{ truncateAddress(row.currentAddress) }}</template></el-table-column>
            <el-table-column prop="issuingAuthority" label="签发机关" min-width="120" show-overflow-tooltip />
            <el-table-column prop="issueDate" label="签发日期" min-width="110" />
            <el-table-column label="有效期" min-width="180"><template #default="{row}">{{ row.validFrom || '-' }} ~ {{ row.validUntil || '-' }}</template></el-table-column>
            <el-table-column label="状态" min-width="80"><template #default="{row}"><StatusTag :value="row.status" kind="residencePermit" /></template></el-table-column>
            <el-table-column label="操作" min-width="220" fixed="right">
              <template #default="{row}">
                <el-button link type="primary" @click="router.push(`/residence-permits/${row.permitId}`)">详情</el-button>
                <el-button v-if="row.status === 'ACTIVE' && canApply" link type="success" @click="goEndorsement(row)">申请签注</el-button>
                <el-button v-if="row.status === 'ACTIVE' && canApply" link type="danger" @click="goCancellation(row)">申请注销</el-button>
              </template>
            </el-table-column>
          </el-table>
          <AppPagination v-model:current="pager.current" v-model:size="pager.size" :total="pager.total" />
        </el-card>
      </el-tab-pane>

      <el-tab-pane label="即将到期" name="expiring">
        <ExpiringPermitList />
      </el-tab-pane>
    </el-tabs>

    <FirstIssueDialog v-model="createVisible" @confirm="goCreateFirstIssue" />
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import SearchPanel from '../../components/common/SearchPanel.vue'
import AppPagination from '../../components/common/AppPagination.vue'
import StatusTag from '../../components/common/StatusTag.vue'
import SensitiveText from '../../components/common/SensitiveText.vue'
import ExpiringPermitList from './ExpiringPermitList.vue'
import FirstIssueDialog from './components/FirstIssueDialog.vue'
import { getResidencePermitPage } from '../../api/floatingResidence'
import { normalizeResidencePermitList } from '../../adapters/residencePermit'
import { normalizePageResult } from '../../utils/page'
import { RESIDENCE_PERMIT_STATUS } from '../../constants/floatingResidence'
import { PERMISSIONS } from '../../constants/permissions'
import { useUserStore } from '../../stores/user'
import { getApiErrorMessage } from '../../utils/apiError'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const loading = ref(false)
const records = ref([])
const activeTab = ref('list')
const createVisible = ref(false)
const pager = reactive({ current: 1, size: 20, total: 0 })
const query = reactive({ permitNo: '', personName: '', identityNo: '', currentRegionCode: '', status: '', validFrom: '', validTo: '' })

const canApply = computed(() => userStore.hasPermission(PERMISSIONS.RESIDENCE_PERMIT_APPLY))

function truncateAddress(addr) {
  if (!addr) return '-'
  return addr.length > 15 ? addr.slice(0, 15) + '…' : addr
}

async function load() {
  if (activeTab.value !== 'list') return
  loading.value = true
  try {
    const res = await getResidencePermitPage({ ...query, current: pager.current, size: pager.size })
    const page = normalizePageResult(res)
    records.value = normalizeResidencePermitList(page.records)
    pager.total = page.total
    pager.current = page.current
    pager.size = page.size
  } catch (error) {
    ElMessage.error(getApiErrorMessage(error, '查询居住证失败'))
  } finally { loading.value = false }
}

function handleSearch() { pager.current = 1; load() }
function handleReset() {
  Object.keys(query).forEach(k => query[k] = '')
  pager.current = 1
  load()
}

function goEndorsement(row) { router.push(`/residence-permits/${row.permitId}/endorsement/apply`) }
function goCancellation(row) { router.push(`/residence-permits/${row.permitId}/cancellation/apply`) }

function openCreate() { createVisible.value = true }
function goCreateFirstIssue(payload) {
  createVisible.value = false
  router.push(`/residence-permits/first-issue?floatingId=${payload.floatingId}`)
}

function onTabChange(tab) {
  if (tab === 'list') load()
}

// 外部路由 /residence-permits/expiring 自动激活到期页签
onMounted(() => {
  if (route.path === '/residence-permits/expiring') activeTab.value = 'expiring'
  else load()
})

watch(() => pager.current, () => load())
watch(() => pager.size, () => { pager.current = 1; load() })
</script>

<style scoped>
.page-container{display:flex;flex-direction:column;gap:16px}
.page-header{display:flex;justify-content:space-between;align-items:end;flex-wrap:wrap;gap:12px}
.page-header h1{margin:0 0 8px}
.subtitle{margin:0;color:var(--el-text-color-secondary)}
.header-actions{display:flex;gap:8px;flex-wrap:wrap}
</style>
