<template>
  <div class="page-container" v-loading="loading">
    <div class="page-header">
      <div>
        <h1>申请详情</h1>
        <p class="subtitle">审批通过不等于业务已办结，办结状态只能由后端执行结果确认。</p>
      </div>
      <el-button @click="router.back()">返回</el-button>
    </div>

    <el-card v-if="application" shadow="never">
      <template #header>
        <div class="card-header">
          <span>{{ application.applicationNo }}</span>
          <StatusTag :value="application.status" kind="application" />
        </div>
      </template>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="业务类型">
          {{ BUSINESS_TYPE_LABEL[application.businessType] || application.businessType }}
        </el-descriptions-item>
        <el-descriptions-item label="申请人">{{ application.applicantName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="申请标题">{{ application.title }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ formatDateTime(application.createdAt) }}</el-descriptions-item>
        <el-descriptions-item label="申请原因" :span="2">{{ application.reason }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ application.remark || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <!-- 旧三类专用面板（行为保持不变） -->
    <MigrationDetailPanel
      v-if="handlerFamily === 'migration' && professionalDetail"
      :detail="professionalDetail"
      :person="migrationPerson"
    />
    <FloatingResidenceDetailPanel
      v-if="handlerFamily === 'floating' && professionalDetail"
      mode="floating"
      :detail="displayDetail"
      :subject="subjectDetail"
    />
    <FloatingResidenceDetailPanel
      v-if="handlerFamily === 'permit' && professionalDetail"
      mode="permit"
      :detail="displayDetail"
      :subject="subjectDetail"
    />
    <!-- 新业务统一字段面板 -->
    <ProfessionalFieldsPanel
      v-if="genericDetailFields.length"
      :title="genericDetailTitle"
      :fields="genericDetailFields"
      :status="genericDetailStatus"
      :status-kind="genericStatusKind"
      :unavailable-reason="unavailableReason"
    />

    <div v-if="canExecute" class="execute-bar">
      <span>{{ executeHintText }}</span>
      <el-button type="success" :loading="executing" @click="openExecuteDialog">
        执行{{ executeLabelShort }}
      </el-button>
    </div>

    <el-card shadow="never">
      <template #header>申请材料</template>
      <MaterialUploader
        v-if="canShowMaterialUploader"
        :application-id="applicationId"
        :material-options="materialOptions"
        :material-rule-text="materialRuleText"
        @uploaded="load"
      />
      <MaterialList :materials="materials" :can-delete="isDraft && canDelete" @changed="load" />
    </el-card>

    <el-card shadow="never">
      <template #header>审批轨迹</template>
      <ApprovalTimeline :logs="logs" />
    </el-card>

    <ApplicationActionBar
      :application="application"
      :loading="actionLoading"
      :specialized-edit-route="specializedEditRoute"
      :can-continue-specialized="canContinueSpecialized"
      @continue-specialized="continueSpecialized"
      @submit="submit"
      @withdraw="withdraw"
      @cancel="cancelDraft"
    />
  </div>

  <FloatingResidenceExecuteDialog
    v-model="executeVisible"
    :execute-type="executeDialogType"
    :version="executeVersion"
    :loading="executing"
    @confirm="handleExecute"
  />
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
import ProfessionalFieldsPanel from '../../components/business/ProfessionalFieldsPanel.vue'
import MigrationDetailPanel from '../migrations/components/MigrationDetailPanel.vue'
import FloatingResidenceDetailPanel from '../floating/components/FloatingResidenceDetailPanel.vue'
import FloatingResidenceExecuteDialog from '../floating/components/FloatingResidenceExecuteDialog.vue'
import {
  cancelDraftApplication,
  getApplicationApprovalLogs,
  getApplicationDetail,
  submitApplication,
  withdrawApplication
} from '../../api/applications'
import { getMaterials } from '../../api/materials'
import { getPersonById } from '../../api/persons'
import { getMigrationRecord } from '../../adapters/migration'
import { normalizePerson } from '../../adapters/person'
import { BUSINESS_TYPE_LABEL } from '../../constants/application'
import { PERMISSIONS } from '../../constants/permissions'
import { useUserStore } from '../../stores/user'
import { formatDateTime } from '../../utils/date'
import { getApiErrorMessage, isApiConflict } from '../../utils/apiError'
import { getApplicationBusinessHandler } from '../../features/applications/handlers'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const applicationId = computed(() => route.params.applicationId)
const loading = ref(false)
const actionLoading = ref(false)
const executing = ref(false)
const application = ref(null)
/** 单一专业详情，不再按 family 拆多组 ref */
const professionalDetail = ref(null)
const migrationPerson = ref(null)
const materials = ref([])
const logs = ref([])
const executeVisible = ref(false)
const executeDialogType = ref('')
const executeVersion = ref(null)

const handler = computed(() => getApplicationBusinessHandler(application.value?.businessType))
const handlerFamily = computed(() => handler.value?.family || '')

const displayDetail = computed(() => {
  if (!handler.value || !professionalDetail.value) return null
  return handler.value.getDisplayDetail(professionalDetail.value)
})

const subjectDetail = computed(() => {
  if (!handler.value || !professionalDetail.value) return null
  return handler.value.getSubject(professionalDetail.value)
})

const executionMeta = computed(() => {
  if (!handler.value) return null
  return handler.value.getExecutionMeta({
    businessType: application.value?.businessType,
    detail: professionalDetail.value
  })
})

const hasExecutableVersion = computed(() => Number.isInteger(executionMeta.value?.version))

const isDraft = computed(() => application.value?.status === 'DRAFT')
const canUpload = computed(() => userStore.hasPermission(PERMISSIONS.MATERIAL_UPLOAD))
const canDelete = computed(() => userStore.hasPermission(PERMISSIONS.MATERIAL_DELETE))

const materialOptions = computed(() => {
  if (!handler.value) return []
  return handler.value.getMaterialOptions({
    businessType: application.value?.businessType,
    detail: professionalDetail.value
  }) || []
})

const materialRuleText = computed(() => {
  if (!handler.value) return ''
  return handler.value.getMaterialRuleText({
    businessType: application.value?.businessType,
    detail: professionalDetail.value
  }) || ''
})

/** 草稿 + Handler 支持材料 + 上传权限（不再仅限 migration） */
const canShowMaterialUploader = computed(() => {
  return isDraft.value && canUpload.value && Boolean(handler.value) && materialOptions.value.length > 0
})

const specializedEditRoute = computed(() => {
  if (!handler.value) return null
  return handler.value.buildEditRoute({
    applicationId: applicationId.value,
    detail: professionalDetail.value
  })
})

const canContinueSpecialized = computed(() => {
  if (!isDraft.value) return false
  if (!userStore.hasPermission(PERMISSIONS.APPLICATION_EDIT)) return false
  if (!handler.value) return false
  if (!specializedEditRoute.value) return false
  const permission = handler.value.getEditPermission(application.value?.businessType)
  if (permission && userStore.hasPermission(permission)) return true
  return false
})

const isExecutableByBackend = computed(() => {
  if (!professionalDetail.value) return false
  // 后端统一返回 executable；缺省时旧数据兼容为 true（仅当已有 version）
  if (professionalDetail.value.executable === false) return false
  return true
})

const canExecute = computed(() => {
  if (application.value?.status !== 'APPROVED') return false
  if (!hasExecutableVersion.value) return false
  if (!isExecutableByBackend.value) return false
  const permission = executionMeta.value?.permission
  return Boolean(permission && userStore.hasPermission(permission))
})

const executeHintText = computed(() => {
  return executionMeta.value?.message
    || '执行后将由后端正式变更业务状态，请确认信息无误。'
})

const executeLabelShort = computed(() => executionMeta.value?.type || '业务')

const genericDetailFields = computed(() => {
  if (!handler.value || !professionalDetail.value) return []
  // 旧三类使用专用面板，不走通用字段
  if (['migration', 'floating', 'permit'].includes(handlerFamily.value)) return []
  if (typeof handler.value.getDetailFields === 'function') {
    return handler.value.getDetailFields(professionalDetail.value) || []
  }
  return []
})

const genericDetailTitle = computed(() => {
  if (!handler.value || typeof handler.value.getDetailTitle !== 'function') return '专业业务信息'
  return handler.value.getDetailTitle(professionalDetail.value) || '专业业务信息'
})

const genericDetailStatus = computed(() => {
  if (!handler.value || typeof handler.value.getDetailStatus !== 'function') return ''
  return handler.value.getDetailStatus(professionalDetail.value) || ''
})

const genericStatusKind = computed(() => {
  if (!handler.value || typeof handler.value.getDetailStatusKind !== 'function') return 'application'
  return handler.value.getDetailStatusKind() || 'application'
})

const unavailableReason = computed(() => professionalDetail.value?.unavailableReason || '')

async function loadMigrationPerson(record) {
  migrationPerson.value = null
  if (!record?.personId) return
  try {
    migrationPerson.value = normalizePerson(await getPersonById(record.personId))
  } catch {
    /* optional enrichment */
  }
}

async function load() {
  loading.value = true
  try {
    application.value = await getApplicationDetail(applicationId.value)
    const [materialResult, logResult] = await Promise.all([
      getMaterials(applicationId.value),
      getApplicationApprovalLogs(applicationId.value)
    ])
    materials.value = materialResult || []
    logs.value = logResult || []
    professionalDetail.value = null
    migrationPerson.value = null

    const currentHandler = getApplicationBusinessHandler(application.value?.businessType)
    if (currentHandler) {
      const rawDetail = await currentHandler.loadDetail(
        applicationId.value,
        application.value?.businessType
      )
      const normalized = typeof currentHandler.normalizeDetail === 'function'
        ? currentHandler.normalizeDetail(rawDetail)
        : rawDetail
      professionalDetail.value = normalized

      // 材料/日志优先通用接口，回退专业详情内嵌字段
      materials.value = materialResult || normalized?.materials || []
      logs.value = logResult || normalized?.approvalLogs || []

      if (currentHandler.family === 'migration') {
        await loadMigrationPerson(getMigrationRecord(normalized))
      }
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
  const routeObj = specializedEditRoute.value
  if (routeObj) router.push(routeObj)
}

async function submit() {
  await ElMessageBox.confirm('提交后不能继续编辑草稿，确认提交吗？', '提交申请', { type: 'warning' })
  actionLoading.value = true
  try {
    const currentHandler = handler.value
    if (currentHandler?.submit) {
      await currentHandler.submit({
        businessType: application.value.businessType,
        applicationId: applicationId.value,
        detail: professionalDetail.value
      })
    } else {
      await submitApplication(applicationId.value)
    }
    ElMessage.success('申请已提交')
    await load()
  } catch (error) {
    if (isApiConflict(error)) await load()
    ElMessage.error(getApiErrorMessage(error, '提交失败'))
  } finally {
    actionLoading.value = false
  }
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
  } finally {
    actionLoading.value = false
  }
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
  } finally {
    actionLoading.value = false
  }
}

function openExecuteDialog() {
  const meta = executionMeta.value
  if (!meta || !handler.value) return

  if (meta.mode === 'direct-confirm') {
    executeDirectConfirm(meta)
    return
  }

  // dialog 模式：沿用现有 FloatingResidenceExecuteDialog
  executeDialogType.value = meta.dialogType || 'FLOATING_EXECUTE'
  executeVersion.value = meta.version
  executeVisible.value = true
}

async function executeDirectConfirm(meta) {
  await ElMessageBox.confirm(
    meta.message || '确认执行该业务吗？',
    meta.title || '执行业务',
    { type: 'warning' }
  )
  executing.value = true
  try {
    await handler.value.execute({
      businessType: application.value.businessType,
      applicationId: applicationId.value,
      detail: professionalDetail.value
    })
    const refreshed = await load()
    const completed = refreshed && handler.value.isCompleted({
      application: application.value,
      detail: professionalDetail.value
    })
    if (completed) ElMessage.success('业务执行完成')
    else ElMessage.warning('执行请求已受理，但未确认业务办结，请刷新查看。')
  } catch (error) {
    if (isApiConflict(error)) await load()
    ElMessage.error(getApiErrorMessage(error, '业务执行失败'))
  } finally {
    executing.value = false
  }
}

async function handleExecute(payload) {
  executing.value = true
  try {
    await handler.value.execute({
      businessType: application.value.businessType,
      applicationId: applicationId.value,
      detail: professionalDetail.value,
      payload
    })

    const refreshed = await load()
    const appCompleted = refreshed && application.value?.status === 'COMPLETED'
    const proCompleted = handler.value.isCompleted({
      application: application.value,
      detail: professionalDetail.value
    })

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
  } finally {
    executing.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.page-container { display: flex; flex-direction: column; gap: 16px; }
.page-header { display: flex; justify-content: space-between; align-items: end; }
.page-header h1 { margin: 0 0 8px; }
.subtitle { margin: 0; color: var(--el-text-color-secondary); }
.card-header, .execute-bar { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.execute-bar { justify-content: flex-start; }
.execute-bar span { font-size: 13px; color: var(--el-text-color-secondary); }
</style>
