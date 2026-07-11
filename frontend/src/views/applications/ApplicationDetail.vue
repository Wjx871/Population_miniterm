<template>
  <div class="page-container" v-loading="loading">
    <div class="page-header">
      <div><h1>申请详情</h1><p class="subtitle">审批通过不等于业务已办结，办结状态只能由后端执行结果确认。</p></div>
      <el-button @click="router.back()">返回</el-button>
    </div>

    <el-card v-if="application" shadow="never">
      <template #header><div class="card-header"><span>{{ application.applicationNo }}</span><StatusTag :value="application.status" kind="application" /></div></template>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="业务类型">{{ BUSINESS_TYPE_LABEL[application.businessType] || application.businessType }}</el-descriptions-item>
        <el-descriptions-item label="申请人">{{ application.applicantName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="申请标题">{{ application.title }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ formatDateTime(application.createdAt) }}</el-descriptions-item>
        <el-descriptions-item label="申请原因" :span="2">{{ application.reason }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ application.remark || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <MigrationDetailPanel v-if="migrationDetail" :detail="migrationDetail" :person="migrationPerson" />
    <FloatingResidenceDetailPanel v-if="floatingDetail" mode="floating" :detail="floatingDetailBody" />
    <FloatingResidenceDetailPanel v-if="permitDetail" mode="permit" :detail="permitDetailBody" />
    <div v-if="canExecute" class="execute-bar">
      <span>{{ executeLabelText }}</span>
      <el-button type="success" :loading="executing" @click="openExecuteDialog">执行{{ executeLabelShort }}</el-button>
    </div>

    <el-card shadow="never">
      <template #header>申请材料</template>
      <MaterialUploader v-if="isDraft && isMigrationApplication && canUpload" :application-id="applicationId" :material-options="materialOptions" :material-rule-text="materialRuleText" @uploaded="load" />
      <MaterialList :materials="materials" :can-delete="isDraft && canDelete" @changed="load" />
    </el-card>
    <el-card shadow="never"><template #header>审批轨迹</template><ApprovalTimeline :logs="logs" /></el-card>
    <ApplicationActionBar :application="application" :loading="actionLoading" :specialized-edit-route="specializedEditRoute" :can-continue-specialized="canContinueSpecialized" @continue-specialized="continueSpecialized" @submit="submit" @withdraw="withdraw" @cancel="cancelDraft" />
  </div>
  <FloatingResidenceExecuteDialog v-model="executeVisible" :execute-type="executeType" :version="executeVersion" :loading="executing" @confirm="handleExecute" />
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import StatusTag from '../../components/common/StatusTag.vue'
import MaterialUploader from '../../components/business/MaterialUploader.vue'
import MaterialList from '../../components/business/MaterialList.vue'
import ApprovalTimeline from '../../components/business/ApprovalTimeline.vue'
import ApplicationActionBar from '../../components/business/ApplicationActionBar.vue'
import MigrationDetailPanel from '../migrations/components/MigrationDetailPanel.vue'
import FloatingResidenceDetailPanel from '../floating/components/FloatingResidenceDetailPanel.vue'
import FloatingResidenceExecuteDialog from '../floating/components/FloatingResidenceExecuteDialog.vue'
import { cancelDraftApplication, getApplicationApprovalLogs, getApplicationDetail, submitApplication, withdrawApplication } from '../../api/applications'
import { executeMigrationIn, executeMigrationOut, getMigrationApplicationDetail } from '../../api/migrations'
import { getFloatingApplicationDetail, executeFloatingApplication, getPermitApplicationDetail, issueResidencePermit, endorseResidencePermit, cancelResidencePermitApplication } from '../../api/floatingResidence'
import { getMaterials } from '../../api/materials'
import { getPersonById } from '../../api/persons'
import { getMigrationRecord } from '../../adapters/migration'
import { normalizePerson } from '../../adapters/person'
import { normalizeFloatingProfessional } from '../../adapters/floating'
import { normalizePermitProfessional } from '../../adapters/residencePermit'
import { BUSINESS_TYPE, BUSINESS_TYPE_LABEL } from '../../constants/application'
import { getMigrationMaterialOptions, getMigrationMaterialRuleText } from '../../constants/material'
import { getFloatingMaterialOptions, getFloatingMaterialRuleText, getPermitMaterialOptions, getPermitMaterialRuleText, EXECUTE_TYPE } from '../../constants/floatingResidence'
import { PERMISSIONS } from '../../constants/permissions'
import { useUserStore } from '../../stores/user'
import { formatDateTime } from '../../utils/date'
import { getApiErrorMessage, isApiConflict } from '../../utils/apiError'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const applicationId = computed(() => route.params.applicationId)
const loading = ref(false)
const actionLoading = ref(false)
const executing = ref(false)
const application = ref(null)
const migrationDetail = ref(null)
const migrationPerson = ref(null)
const floatingDetail = ref(null)
const permitDetail = ref(null)
const materials = ref([])
const logs = ref([])
const executeVisible = ref(false)
const executeType = ref('')
const executeVersion = ref(0)

const isMigrationApplication = computed(() => [BUSINESS_TYPE.MIGRATION_IN, BUSINESS_TYPE.MIGRATION_OUT].includes(application.value?.businessType))
const isFloatingApplication = computed(() => application.value?.businessType === BUSINESS_TYPE.FLOATING_REGISTRATION)
const isPermitApplication = computed(() => [BUSINESS_TYPE.RESIDENCE_PERMIT_FIRST_ISSUE, BUSINESS_TYPE.RESIDENCE_PERMIT_ENDORSEMENT, BUSINESS_TYPE.RESIDENCE_PERMIT_CANCELLATION].includes(application.value?.businessType))
const isProfessionalBusiness = computed(() => isMigrationApplication.value || isFloatingApplication.value || isPermitApplication.value)
const migration = computed(() => getMigrationRecord(migrationDetail.value))
const floatingDetailBody = computed(() => floatingDetail.value?.professional)
const permitDetailBody = computed(() => permitDetail.value?.professional)
const isDraft = computed(() => application.value?.status === 'DRAFT')
const canUpload = computed(() => userStore.hasPermission(PERMISSIONS.MATERIAL_UPLOAD))
const canDelete = computed(() => userStore.hasPermission(PERMISSIONS.MATERIAL_DELETE))
const specializedEditRoute = computed(() => {
  if (isMigrationApplication.value) {
    return application.value?.businessType === BUSINESS_TYPE.MIGRATION_IN ? '/migrations/in/apply' : '/migrations/out/apply'
  }
  if (isFloatingApplication.value) return '/floating-population/apply'
  if (isPermitApplication.value) {
    const bt = application.value?.businessType
    if (bt === BUSINESS_TYPE.RESIDENCE_PERMIT_FIRST_ISSUE) return '/residence-permits/first-issue'
    if (bt === BUSINESS_TYPE.RESIDENCE_PERMIT_ENDORSEMENT) {
      const pid = permitDetailBody.value?.permitId
      return pid ? `/residence-permits/${pid}/endorsement/apply` : ''
    }
    if (bt === BUSINESS_TYPE.RESIDENCE_PERMIT_CANCELLATION) {
      const pid = permitDetailBody.value?.permitId
      return pid ? `/residence-permits/${pid}/cancellation/apply` : ''
    }
    return '/residence-permits/first-issue'
  }
  return ''
})

const canContinueSpecialized = computed(() => {
  if (!isDraft.value) return false
  if (!userStore.hasPermission(PERMISSIONS.APPLICATION_EDIT)) return false
  if (isMigrationApplication.value) {
    return application.value?.businessType === BUSINESS_TYPE.MIGRATION_IN
      ? userStore.hasPermission(PERMISSIONS.MIGRATION_IN_CREATE)
      : userStore.hasPermission(PERMISSIONS.MIGRATION_OUT_CREATE)
  }
  if (isFloatingApplication.value) return userStore.hasPermission(PERMISSIONS.FLOATING_EDIT)
  if (isPermitApplication.value) return userStore.hasPermission(PERMISSIONS.RESIDENCE_PERMIT_APPLY)
  return false
})

const canExecute = computed(() => {
  if (application.value?.status !== 'APPROVED') return false
  if (isMigrationApplication.value && migrationDetail.value?.executable && userStore.hasPermission(PERMISSIONS.MIGRATION_EXECUTE)) return true
  if (isFloatingApplication.value && floatingDetail.value?.executable && userStore.hasPermission(PERMISSIONS.FLOATING_EXECUTE)) return true
  if (isPermitApplication.value && permitDetail.value?.executable) {
    const bt = application.value?.businessType
    if (bt === BUSINESS_TYPE.RESIDENCE_PERMIT_FIRST_ISSUE && userStore.hasPermission(PERMISSIONS.RESIDENCE_PERMIT_ISSUE)) return true
    if (bt === BUSINESS_TYPE.RESIDENCE_PERMIT_ENDORSEMENT && userStore.hasPermission(PERMISSIONS.RESIDENCE_PERMIT_ENDORSE)) return true
    if (bt === BUSINESS_TYPE.RESIDENCE_PERMIT_CANCELLATION && userStore.hasPermission(PERMISSIONS.RESIDENCE_PERMIT_CANCEL)) return true
    return false
  }
  return false
})

const executeLabelText = computed(() => {
  if (isMigrationApplication.value) return '执行后会重新读取申请与迁移状态，确认均为"已办结"才显示成功。'
  if (isFloatingApplication.value) return '执行后将生成正式流动登记记录。'
  if (isPermitApplication.value) return '执行后将生成/变更正式居住证状态。'
  return ''
})

const executeLabelShort = computed(() => {
  if (isMigrationApplication.value) return migrationDetail.value?.migrationIn ? '迁入' : '迁出'
  if (isFloatingApplication.value) return '流动登记'
  const bt = application.value?.businessType
  if (bt === BUSINESS_TYPE.RESIDENCE_PERMIT_FIRST_ISSUE) return '签发'
  if (bt === BUSINESS_TYPE.RESIDENCE_PERMIT_ENDORSEMENT) return '签注'
  if (bt === BUSINESS_TYPE.RESIDENCE_PERMIT_CANCELLATION) return '注销'
  return '业务'
})

const materialOptions = computed(() => {
  if (isMigrationApplication.value) {
    return getMigrationMaterialOptions(migrationDetail.value?.migrationIn ? 'in' : 'out', migration.value?.migrationType)
  }
  if (isFloatingApplication.value) {
    return getFloatingMaterialOptions(floatingDetailBody.value?.residenceReasonCode)
  }
  if (isPermitApplication.value) {
    const bt = application.value?.businessType
    let at = 'FIRST_ISSUE'
    if (bt === BUSINESS_TYPE.RESIDENCE_PERMIT_ENDORSEMENT) at = 'ENDORSEMENT'
    if (bt === BUSINESS_TYPE.RESIDENCE_PERMIT_CANCELLATION) at = 'CANCELLATION'
    return getPermitMaterialOptions(at, floatingDetailBody.value?.residenceReasonCode)
  }
  return []
})

const materialRuleText = computed(() => {
  if (isMigrationApplication.value) {
    return getMigrationMaterialRuleText(migrationDetail.value?.migrationIn ? 'in' : 'out', migration.value?.migrationType)
  }
  if (isFloatingApplication.value) {
    return getFloatingMaterialRuleText(floatingDetailBody.value?.residenceReasonCode)
  }
  if (isPermitApplication.value) {
    const bt = application.value?.businessType
    let at = 'FIRST_ISSUE'
    if (bt === BUSINESS_TYPE.RESIDENCE_PERMIT_ENDORSEMENT) at = 'ENDORSEMENT'
    if (bt === BUSINESS_TYPE.RESIDENCE_PERMIT_CANCELLATION) at = 'CANCELLATION'
    return getPermitMaterialRuleText(at, floatingDetailBody.value?.residenceReasonCode)
  }
  return ''
})

async function loadMigrationPerson(record) {
  migrationPerson.value = null
  if (!record?.personId) return
  try { migrationPerson.value = normalizePerson(await getPersonById(record.personId)) } catch { /* detail remains usable without an optional name lookup */ }
}

async function load() {
  loading.value = true
  try {
    application.value = await getApplicationDetail(applicationId.value)
    const [materialResult, logResult] = await Promise.all([getMaterials(applicationId.value), getApplicationApprovalLogs(applicationId.value)])
    materials.value = materialResult || []
    logs.value = logResult || []
    migrationDetail.value = null
    migrationPerson.value = null
    floatingDetail.value = null
    permitDetail.value = null
    if (isMigrationApplication.value) {
      migrationDetail.value = await getMigrationApplicationDetail(applicationId.value)
      materials.value = materialResult || migrationDetail.value.materials || []
      logs.value = logResult || migrationDetail.value.approvalLogs || []
      await loadMigrationPerson(migration.value)
    } else if (isFloatingApplication.value) {
      floatingDetail.value = normalizeFloatingProfessional(await getFloatingApplicationDetail(applicationId.value))
      materials.value = materialResult || floatingDetail.value.materials || []
      logs.value = logResult || floatingDetail.value?.approvalLogs || []
    } else if (isPermitApplication.value) {
      permitDetail.value = normalizePermitProfessional(await getPermitApplicationDetail(applicationId.value))
      materials.value = materialResult || permitDetail.value.materials || []
      logs.value = logResult || permitDetail.value?.approvalLogs || []
    }
    return true
  } catch (error) {
    ElMessage.error(getApiErrorMessage(error, '加载申请详情失败'))
    return false
  } finally {
    loading.value = false
  }
}

function continueSpecialized() {
  const path = specializedEditRoute.value
  if (path) router.push({ path, query: { applicationId: applicationId.value } })
}

async function submit() {
  await ElMessageBox.confirm('提交后不能继续编辑草稿，确认提交吗？', '提交申请', { type: 'warning' })
  actionLoading.value = true
  try {
    await submitApplication(applicationId.value)
    ElMessage.success('申请已提交')
    await load()
  } catch (error) {
    if (isApiConflict(error)) await load()
    ElMessage.error(getApiErrorMessage(error, '提交失败'))
  } finally { actionLoading.value = false }
}

async function withdraw() {
  await ElMessageBox.confirm('确认撤回该申请吗？', '撤回申请', { type: 'warning' })
  actionLoading.value = true
  try {
    await withdrawApplication(applicationId.value)
    ElMessage.success('申请已撤回')
    await load()
  } catch (error) {
    if (isApiConflict(error)) await load()
    ElMessage.error(getApiErrorMessage(error, '撤回失败'))
  } finally { actionLoading.value = false }
}

async function cancelDraft() {
  await ElMessageBox.confirm('确认取消该草稿吗？取消后不能恢复。', '取消草稿', { type: 'warning' })
  actionLoading.value = true
  try {
    await cancelDraftApplication(applicationId.value)
    ElMessage.success('草稿已取消')
    await router.replace('/applications')
  } catch (error) {
    if (isApiConflict(error)) await load()
    ElMessage.error(getApiErrorMessage(error, '取消草稿失败'))
  } finally { actionLoading.value = false }
}

function openExecuteDialog() {
  if (isMigrationApplication.value) {
    executeType.value = EXECUTE_TYPE.FLOATING_EXECUTE // migration uses its own execute, but this signals dialog not needed
    executeVersion.value = migration.value?.version || 0
    executeMigration()
    return
  }
  if (isFloatingApplication.value) {
    executeType.value = EXECUTE_TYPE.FLOATING_EXECUTE
    executeVersion.value = floatingDetailBody.value?.version || 0
  } else if (isPermitApplication.value) {
    const bt = application.value?.businessType
    if (bt === BUSINESS_TYPE.RESIDENCE_PERMIT_FIRST_ISSUE) executeType.value = EXECUTE_TYPE.PERMIT_ISSUE
    else if (bt === BUSINESS_TYPE.RESIDENCE_PERMIT_ENDORSEMENT) executeType.value = EXECUTE_TYPE.PERMIT_ENDORSE
    else executeType.value = EXECUTE_TYPE.PERMIT_CANCEL
    executeVersion.value = permitDetailBody.value?.version || 0
  }
  executeVisible.value = true
}

async function executeMigration() {
  await ElMessageBox.confirm('执行会由后端变更当前户籍并生成归档，确认继续吗？', '执行迁移', { type: 'warning' })
  executing.value = true
  try {
    const record = migration.value
    if (migrationDetail.value?.migrationIn) await executeMigrationIn(applicationId.value, record.version)
    else await executeMigrationOut(applicationId.value, record.version)
    const refreshed = await load()
    const completed = refreshed && application.value?.status === 'COMPLETED' && migration.value?.businessStatus === 'COMPLETED'
    if (completed) ElMessage.success('业务执行完成')
    else ElMessage.warning('执行请求已受理，但未确认业务办结，请刷新查看。')
  } catch (error) {
    if (isApiConflict(error)) await load()
    ElMessage.error(getApiErrorMessage(error, '业务执行失败'))
  } finally { executing.value = false }
}

async function handleExecute(payload) {
  executing.value = true
  try {
    if (isFloatingApplication.value) {
      await executeFloatingApplication(applicationId.value, payload.version)
    } else if (isPermitApplication.value) {
      const bt = application.value?.businessType
      if (bt === BUSINESS_TYPE.RESIDENCE_PERMIT_FIRST_ISSUE) await issueResidencePermit(applicationId.value, { issuingAuthority: payload.issuingAuthority, version: payload.version })
      else if (bt === BUSINESS_TYPE.RESIDENCE_PERMIT_ENDORSEMENT) await endorseResidencePermit(applicationId.value, payload.version)
      else await cancelResidencePermitApplication(applicationId.value, payload.version)
    }
    const refreshed = await load()
    const appCompleted = refreshed && application.value?.status === 'COMPLETED'
    const pro = isFloatingApplication.value ? floatingDetailBody.value : permitDetailBody.value
    const proCompleted = pro?.businessStatus === 'COMPLETED'
    if (appCompleted && proCompleted) {
      ElMessage.success('业务执行完成')
    } else if (appCompleted) {
      ElMessage.warning('执行请求已返回，但尚未确认专业业务办结，请刷新查看。')
    } else {
      ElMessage.warning('执行请求已返回，通用申请和专业状态均未确认办结，请刷新查看。')
    }
    executeVisible.value = false
  } catch (error) {
    if (isApiConflict(error)) await load()
    ElMessage.error(getApiErrorMessage(error, '业务执行失败'))
  } finally { executing.value = false }
}

onMounted(load)
</script>

<style scoped>
.page-container{display:flex;flex-direction:column;gap:16px}.page-header{display:flex;justify-content:space-between;align-items:end}.page-header h1{margin:0 0 8px}.subtitle{margin:0;color:var(--el-text-color-secondary)}.card-header,.execute-bar{display:flex;align-items:center;justify-content:space-between;gap:12px}.execute-bar{justify-content:flex-start}.execute-bar span{font-size:13px;color:var(--el-text-color-secondary)}
</style>
