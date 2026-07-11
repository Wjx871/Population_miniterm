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
    <div v-if="canExecute" class="execute-bar">
      <el-button type="success" :loading="executing" @click="execute">执行{{ migrationDetail?.migrationIn ? '迁入' : '迁出' }}业务</el-button>
      <span>执行后会重新读取申请与迁移状态，确认均为“已办结”才显示成功。</span>
    </div>

    <el-card shadow="never">
      <template #header>申请材料</template>
      <MaterialUploader v-if="isDraft && isMigrationApplication && canUpload" :application-id="applicationId" :material-options="materialOptions" :material-rule-text="materialRuleText" @uploaded="load" />
      <MaterialList :materials="materials" :can-delete="isDraft && canDelete" @changed="load" />
    </el-card>
    <el-card shadow="never"><template #header>审批轨迹</template><ApprovalTimeline :logs="logs" /></el-card>
    <ApplicationActionBar :application="application" :loading="actionLoading" :is-migration-draft="isMigrationApplication" @continue-migration="continueMigration" @submit="submit" @withdraw="withdraw" @cancel="cancelDraft" />
  </div>
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
import { cancelDraftApplication, getApplicationApprovalLogs, getApplicationDetail, submitApplication, withdrawApplication } from '../../api/applications'
import { executeMigrationIn, executeMigrationOut, getMigrationApplicationDetail } from '../../api/migrations'
import { getMaterials } from '../../api/materials'
import { getPersonById } from '../../api/persons'
import { getMigrationRecord } from '../../adapters/migration'
import { normalizePerson } from '../../adapters/person'
import { BUSINESS_TYPE, BUSINESS_TYPE_LABEL } from '../../constants/application'
import { getMigrationMaterialOptions, getMigrationMaterialRuleText } from '../../constants/material'
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
const materials = ref([])
const logs = ref([])

const isMigrationApplication = computed(() => [BUSINESS_TYPE.MIGRATION_IN, BUSINESS_TYPE.MIGRATION_OUT].includes(application.value?.businessType))
const migration = computed(() => getMigrationRecord(migrationDetail.value))
const isDraft = computed(() => application.value?.status === 'DRAFT')
const canUpload = computed(() => userStore.hasPermission(PERMISSIONS.MATERIAL_UPLOAD))
const canDelete = computed(() => userStore.hasPermission(PERMISSIONS.MATERIAL_DELETE))
const canExecute = computed(() => isMigrationApplication.value && application.value?.status === 'APPROVED' && migrationDetail.value?.executable && userStore.hasPermission(PERMISSIONS.MIGRATION_EXECUTE))
const materialOptions = computed(() => getMigrationMaterialOptions(migrationDetail.value?.migrationIn ? 'in' : 'out', migration.value?.migrationType))
const materialRuleText = computed(() => getMigrationMaterialRuleText(migrationDetail.value?.migrationIn ? 'in' : 'out', migration.value?.migrationType))

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
    if (isMigrationApplication.value) {
      migrationDetail.value = await getMigrationApplicationDetail(applicationId.value)
      materials.value = materialResult || migrationDetail.value.materials || []
      logs.value = logResult || migrationDetail.value.approvalLogs || []
      await loadMigrationPerson(migration.value)
    }
    return true
  } catch (error) {
    ElMessage.error(getApiErrorMessage(error, '加载申请详情失败'))
    return false
  } finally {
    loading.value = false
  }
}

function continueMigration() {
  const path = application.value?.businessType === BUSINESS_TYPE.MIGRATION_IN ? '/migrations/in/apply' : '/migrations/out/apply'
  router.push({ path, query: { applicationId: applicationId.value } })
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

async function execute() {
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

onMounted(load)
</script>

<style scoped>
.page-container{display:flex;flex-direction:column;gap:16px}.page-header{display:flex;justify-content:space-between;align-items:end}.page-header h1{margin:0 0 8px}.subtitle{margin:0;color:var(--el-text-color-secondary)}.card-header,.execute-bar{display:flex;align-items:center;justify-content:space-between;gap:12px}.execute-bar{justify-content:flex-start}.execute-bar span{font-size:13px;color:var(--el-text-color-secondary)}
</style>
