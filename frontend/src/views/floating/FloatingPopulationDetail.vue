<template>
  <div class="page-container" v-loading="loading">
    <div class="page-header">
      <div><h1>流动登记详情</h1><p class="subtitle">封闭管理的流动人口登记记录。</p></div>
      <div class="header-actions">
        <el-button v-if="record?.sourceApplicationId" @click="router.push(`/applications/${record.sourceApplicationId}`)">查看来源申请</el-button>
        <el-button @click="backToList">返回列表</el-button>
        <el-button v-if="record?.status === 'ACTIVE' && canApplyPermit" type="success" @click="goFirstIssue">首次申领居住证</el-button>
        <el-button v-if="record?.status === 'ACTIVE' && canClose" type="danger" @click="closeVisible = true">关闭登记</el-button>
      </div>
    </div>

    <el-card shadow="never">
      <template #header><div class="card-header"><span>基本信息</span><StatusTag :value="record?.status" kind="floating" /></div></template>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="登记编号">{{ record?.registrationNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="人员">{{ record?.personName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="身份证号"><SensitiveText :value="record?.identityNo" kind="idCard" /></el-descriptions-item>
        <el-descriptions-item label="手机号"><SensitiveText :value="record?.phone" kind="phone" /></el-descriptions-item>
        <el-descriptions-item label="来源区划">{{ record?.sourceRegionCode || '-' }}</el-descriptions-item>
        <el-descriptions-item label="来源地址">{{ record?.sourceAddress || '-' }}</el-descriptions-item>
        <el-descriptions-item label="当前区划">{{ record?.currentRegionCode || '-' }}</el-descriptions-item>
        <el-descriptions-item label="当前地址">{{ record?.currentAddress || '-' }}</el-descriptions-item>
        <el-descriptions-item label="居住事由">{{ RESIDENCE_REASON[record?.residenceReasonCode] || record?.residenceReasonCode || '-' }}</el-descriptions-item>
        <el-descriptions-item label="居住证明类型">{{ RESIDENCE_PROOF_TYPE[record?.residenceProofType] || record?.residenceProofType || '-' }}</el-descriptions-item>
        <el-descriptions-item label="到达日期">{{ record?.arrivalDate || '-' }}</el-descriptions-item>
        <el-descriptions-item label="计划离开日期">{{ record?.plannedLeaveDate || '-' }}</el-descriptions-item>
        <el-descriptions-item label="登记日期">{{ record?.registrationDate || '-' }}</el-descriptions-item>
        <el-descriptions-item label="申领资格日期">{{ record?.eligibleFromDate || '-' }}</el-descriptions-item>
        <el-descriptions-item v-if="record?.status !== 'ACTIVE'" label="关闭原因">{{ CLOSE_REASON[record?.closeReasonCode] || record?.closeReasonCode || '-' }}</el-descriptions-item>
        <el-descriptions-item v-if="record?.closedAt" label="关闭时间">{{ record?.closedAt }}</el-descriptions-item>
        <el-descriptions-item label="版本">{{ record?.version }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <CloseFloatingDialog v-model="closeVisible" :version="record?.version || 0" :loading="closeLoading" @confirm="handleClose" />
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import StatusTag from '../../components/common/StatusTag.vue'
import SensitiveText from '../../components/common/SensitiveText.vue'
import CloseFloatingDialog from './components/CloseFloatingDialog.vue'
import { getFloatingPopulationById, closeFloatingPopulation } from '../../api/floatingResidence'
import { normalizeFloatingPopulation } from '../../adapters/floating'
import { RESIDENCE_REASON, RESIDENCE_PROOF_TYPE, CLOSE_REASON } from '../../constants/floatingResidence'
import { PERMISSIONS } from '../../constants/permissions'
import { useUserStore } from '../../stores/user'
import { getApiErrorMessage, isApiConflict } from '../../utils/apiError'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const loading = ref(false)
const record = ref(null)
const closeVisible = ref(false)
const closeLoading = ref(false)

const canApplyPermit = computed(() => userStore.hasPermission(PERMISSIONS.RESIDENCE_PERMIT_APPLY))
const canClose = computed(() => userStore.hasPermission(PERMISSIONS.FLOATING_CLOSE))
const floatingId = computed(() => route.params.floatingId)

function backToList() { router.push('/floating-population') }
function goFirstIssue() { router.push(`/residence-permits/first-issue?floatingId=${record.value?.floatingId}`) }

async function load() {
  loading.value = true
  try {
    record.value = normalizeFloatingPopulation(await getFloatingPopulationById(floatingId.value))
  } catch (error) {
    ElMessage.error(getApiErrorMessage(error, '加载登记详情失败'))
  } finally { loading.value = false }
}

async function handleClose(payload) {
  closeLoading.value = true
  try {
    await closeFloatingPopulation(floatingId.value, payload)
    ElMessage.success('登记已关闭。后端会同步注销关联的 ACTIVE 居住证。如需核验，请前往居住证列表确认。')
    closeVisible.value = false
    await load()
  } catch (error) {
    if (isApiConflict(error)) await load()
    ElMessage.error(getApiErrorMessage(error, '关闭登记失败'))
  } finally { closeLoading.value = false }
}

onMounted(load)
</script>

<style scoped>
.page-container{display:flex;flex-direction:column;gap:16px}
.page-header{display:flex;justify-content:space-between;align-items:end;flex-wrap:wrap;gap:12px}
.page-header h1{margin:0 0 8px}
.subtitle{margin:0;color:var(--el-text-color-secondary)}
.header-actions{display:flex;gap:8px;flex-wrap:wrap}
.card-header{display:flex;align-items:center;justify-content:space-between;gap:12px}
</style>
