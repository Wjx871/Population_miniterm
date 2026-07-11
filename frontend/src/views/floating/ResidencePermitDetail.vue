<template>
  <div class="page-container" v-loading="loading">
    <div class="page-header">
      <div><h1>居住证详情</h1><p class="subtitle">课程模拟系统生成，不作为真实政务证件。</p></div>
      <div class="header-actions">
        <el-button v-if="record?.sourceApplicationId" @click="router.push(`/applications/${record.sourceApplicationId}`)">查看来源申请</el-button>
        <el-button @click="backToList">返回列表</el-button>
        <el-button v-if="record?.status === 'ACTIVE' && canApply" type="success" @click="goEndorsement">申请签注</el-button>
        <el-button v-if="record?.status === 'ACTIVE' && canApply" type="danger" @click="goCancellation">申请注销</el-button>
      </div>
    </div>

    <el-alert type="warning" :closable="false" show-icon>课程模拟系统生成，不作为真实政务证件。</el-alert>

    <el-card shadow="never">
      <template #header><div class="card-header"><span>证件信息</span><StatusTag :value="record?.status" kind="residencePermit" /></div></template>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="证件编号"><SensitiveText :value="record?.permitNo" kind="text" /></el-descriptions-item>
        <el-descriptions-item label="持证人">{{ record?.personName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="身份证号"><SensitiveText :value="record?.identityNo" kind="idCard" /></el-descriptions-item>
        <el-descriptions-item label="关联流动登记">
          <el-button v-if="record?.floatingId" link type="primary" @click="router.push(`/floating-population/${record.floatingId}`)">{{ record.floatingId }}</el-button>
          <span v-else>-</span>
        </el-descriptions-item>
        <el-descriptions-item label="签发区域">{{ record?.issueRegionCode || '-' }}</el-descriptions-item>
        <el-descriptions-item label="签发机关">{{ record?.issuingAuthority || '-' }}</el-descriptions-item>
        <el-descriptions-item label="签发日期">{{ record?.issueDate || '-' }}</el-descriptions-item>
        <el-descriptions-item label="有效期">{{ record?.validFrom || '-' }} ~ {{ record?.validUntil || '-' }}</el-descriptions-item>
        <el-descriptions-item label="上次签注">{{ record?.lastEndorsedAt || '-' }}</el-descriptions-item>
        <el-descriptions-item v-if="record?.status !== 'ACTIVE'" label="注销原因">{{ record?.cancellationReason || '-' }}</el-descriptions-item>
        <el-descriptions-item v-if="record?.cancelledAt" label="注销时间">{{ record?.cancelledAt }}</el-descriptions-item>
        <el-descriptions-item label="版本">{{ record?.version }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <el-card shadow="never">
      <template #header>生命周期日志</template>
      <PermitLifecycleTimeline :logs="logs" />
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import StatusTag from '../../components/common/StatusTag.vue'
import SensitiveText from '../../components/common/SensitiveText.vue'
import PermitLifecycleTimeline from '../../components/business/PermitLifecycleTimeline.vue'
import { getResidencePermitById, getResidencePermitLogs } from '../../api/floatingResidence'
import { normalizeResidencePermit } from '../../adapters/residencePermit'
import { PERMISSIONS } from '../../constants/permissions'
import { useUserStore } from '../../stores/user'
import { getApiErrorMessage } from '../../utils/apiError'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const loading = ref(false)
const record = ref(null)
const logs = ref([])
const permitId = computed(() => route.params.permitId)
const canApply = computed(() => userStore.hasPermission(PERMISSIONS.RESIDENCE_PERMIT_APPLY))

function backToList() { router.push('/residence-permits') }
function goEndorsement() { router.push(`/residence-permits/${record.value?.permitId}/endorsement/apply`) }
function goCancellation() { router.push(`/residence-permits/${record.value?.permitId}/cancellation/apply`) }

async function load() {
  loading.value = true
  try {
    const [detail, logData] = await Promise.all([
      getResidencePermitById(permitId.value),
      getResidencePermitLogs(permitId.value)
    ])
    record.value = normalizeResidencePermit(detail)
    logs.value = logData || []
  } catch (error) {
    ElMessage.error(getApiErrorMessage(error, '加载居住证详情失败'))
  } finally { loading.value = false }
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
