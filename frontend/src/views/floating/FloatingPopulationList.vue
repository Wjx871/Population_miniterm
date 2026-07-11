<template>
  <div class="page-container" v-loading="loading">
    <div class="page-header">
      <div><h1>流动人口管理</h1><p class="subtitle">流动登记不等于户籍迁入；审批通过不等于正式登记已生成。</p></div>
      <el-button v-if="canCreate" type="primary" @click="router.push('/floating-population/apply')">申请流动登记</el-button>
    </div>

    <SearchPanel @search="handleSearch" @reset="handleReset">
      <el-form :model="query" inline>
        <el-form-item label="登记编号"><el-input v-model="query.registrationNo" clearable placeholder="登记编号" /></el-form-item>
        <el-form-item label="姓名"><el-input v-model="query.personName" clearable placeholder="姓名" /></el-form-item>
        <el-form-item label="身份证号"><el-input v-model="query.identityNo" clearable placeholder="身份证号" /></el-form-item>
        <el-form-item label="当前区划"><el-input v-model="query.currentRegionCode" clearable placeholder="区划编码" /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" clearable placeholder="全部">
            <el-option v-for="(label, code) in FLOATING_STATUS" :key="code" :label="label" :value="code" />
          </el-select>
        </el-form-item>
      </el-form>
    </SearchPanel>

    <el-card shadow="never">
      <el-table :data="records" stripe style="width: 100%">
        <el-table-column prop="registrationNo" label="登记编号" min-width="160" show-overflow-tooltip />
        <el-table-column prop="personName" label="姓名" min-width="100" />
        <el-table-column label="身份证号" min-width="170"><template #default="{row}"><SensitiveText :value="row.identityNo" kind="idCard" /></template></el-table-column>
        <el-table-column label="手机号" min-width="130"><template #default="{row}"><SensitiveText :value="row.phone" kind="phone" /></template></el-table-column>
        <el-table-column prop="sourceRegionCode" label="来源区域" min-width="110" show-overflow-tooltip />
        <el-table-column prop="currentRegionCode" label="当前区域" min-width="110" show-overflow-tooltip />
        <el-table-column label="当前地址" min-width="160" show-overflow-tooltip><template #default="{row}">{{ truncateAddress(row.currentAddress) }}</template></el-table-column>
        <el-table-column prop="arrivalDate" label="到达日期" min-width="110" />
        <el-table-column prop="plannedLeaveDate" label="计划离开" min-width="110" />
        <el-table-column prop="registrationDate" label="登记日期" min-width="110" />
        <el-table-column prop="eligibleFromDate" label="资格日期" min-width="110" />
        <el-table-column label="状态" min-width="90"><template #default="{row}"><StatusTag :value="row.status" kind="floating" /></template></el-table-column>
        <el-table-column label="操作" min-width="280" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="router.push(`/floating-population/${row.floatingId}`)">详情</el-button>
            <el-button v-if="row.status === 'ACTIVE' && canApplyPermit" link type="success" @click="goFirstIssue(row)">首次申领居住证</el-button>
            <el-button v-if="row.status === 'ACTIVE' && canClose" link type="danger" @click="openCloseDialog(row)">关闭登记</el-button>
          </template>
        </el-table-column>
      </el-table>
      <AppPagination v-model:current="pager.current" v-model:size="pager.size" :total="pager.total" />
    </el-card>

    <CloseFloatingDialog v-model="closeVisible" :version="closeVersion" :loading="closeLoading" @confirm="handleClose" />
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import SearchPanel from '../../components/common/SearchPanel.vue'
import AppPagination from '../../components/common/AppPagination.vue'
import StatusTag from '../../components/common/StatusTag.vue'
import SensitiveText from '../../components/common/SensitiveText.vue'
import CloseFloatingDialog from './components/CloseFloatingDialog.vue'
import { getFloatingPopulationPage, closeFloatingPopulation } from '../../api/floatingResidence'
import { normalizeFloatingList } from '../../adapters/floating'
import { normalizePageResult } from '../../utils/page'
import { FLOATING_STATUS } from '../../constants/floatingResidence'
import { PERMISSIONS } from '../../constants/permissions'
import { useUserStore } from '../../stores/user'
import { getApiErrorMessage, isApiConflict } from '../../utils/apiError'

const router = useRouter()
const userStore = useUserStore()
const loading = ref(false)
const records = ref([])
const pager = reactive({ current: 1, size: 20, total: 0 })
const query = reactive({ registrationNo: '', personName: '', identityNo: '', currentRegionCode: '', status: '' })

const closeVisible = ref(false)
const closeLoading = ref(false)
const closeVersion = ref(0)
let closeTargetId = null

const canCreate = computed(() => userStore.hasPermission(PERMISSIONS.FLOATING_CREATE))
const canApplyPermit = computed(() => userStore.hasPermission(PERMISSIONS.RESIDENCE_PERMIT_APPLY))
const canClose = computed(() => userStore.hasPermission(PERMISSIONS.FLOATING_CLOSE))

function truncateAddress(addr) {
  if (!addr) return '-'
  return addr.length > 15 ? addr.slice(0, 15) + '…' : addr
}

async function load() {
  loading.value = true
  try {
    const res = await getFloatingPopulationPage({ ...query, current: pager.current, size: pager.size })
    const page = normalizePageResult(res)
    records.value = normalizeFloatingList(page.records)
    pager.total = page.total
    pager.current = page.current
    pager.size = page.size
  } catch (error) {
    ElMessage.error(getApiErrorMessage(error, '查询流动人口失败'))
  } finally { loading.value = false }
}

function handleSearch() { pager.current = 1; load() }
function handleReset() { query.registrationNo = ''; query.personName = ''; query.identityNo = ''; query.currentRegionCode = ''; query.status = ''; pager.current = 1; load() }

function goFirstIssue(row) {
  router.push(`/residence-permits/first-issue?floatingId=${row.floatingId}`)
}

function openCloseDialog(row) {
  closeTargetId = row.floatingId
  closeVersion.value = row.version
  closeVisible.value = true
}

async function handleClose(payload) {
  closeLoading.value = true
  try {
    await closeFloatingPopulation(closeTargetId, payload)
    ElMessage.success('登记已关闭。后端会同步注销关联的 ACTIVE 居住证。如需核验，请前往居住证列表确认。')
    closeVisible.value = false
    await load()
  } catch (error) {
    if (isApiConflict(error)) await load()
    ElMessage.error(getApiErrorMessage(error, '关闭登记失败'))
  } finally { closeLoading.value = false }
}

onMounted(load)

// 监听分页变化
const { watch } = await import('vue')
watch(() => pager.current, () => load())
watch(() => pager.size, () => { pager.current = 1; load() })
</script>

<style scoped>
.page-container{display:flex;flex-direction:column;gap:16px}
.page-header{display:flex;justify-content:space-between;align-items:end}
.page-header h1{margin:0 0 8px}
.subtitle{margin:0;color:var(--el-text-color-secondary)}
</style>
