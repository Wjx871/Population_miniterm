<template>
  <div class="page-container" v-loading="loading">
    <div class="page-header">
      <div>
        <h1>审批详情</h1>
        <p class="subtitle">审批人须先核验材料并核对专业信息；审批通过后仍需显式执行。</p>
      </div>
      <el-button @click="router.back()">返回</el-button>
    </div>

    <el-card v-if="detail" shadow="never">
      <template #header>
        <div class="card-header">
          <span>{{ detail.approval?.approvalNo }}</span>
          <StatusTag :value="detail.approval?.status" kind="approval" />
        </div>
      </template>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="申请编号">{{ detail.application?.applicationNo }}</el-descriptions-item>
        <el-descriptions-item label="申请人">{{ detail.application?.applicantName }}</el-descriptions-item>
        <el-descriptions-item label="业务类型">
          {{ BUSINESS_TYPE_LABEL[detail.application?.businessType] || detail.application?.businessType || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="审批意见" :span="2">{{ detail.approval?.decisionComment || '-' }}</el-descriptions-item>
        <el-descriptions-item label="申请标题" :span="2">{{ detail.application?.title }}</el-descriptions-item>
        <el-descriptions-item label="申请原因" :span="2">{{ detail.application?.reason }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <!-- 旧三类专用面板 -->
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

    <el-card v-if="detail" shadow="never">
      <template #header>申请材料</template>
      <MaterialList
        :materials="detail.materials"
        :can-verify="canHandle && isPending"
        @changed="load"
      />
    </el-card>

    <el-card v-if="detail" shadow="never">
      <template #header>审批轨迹</template>
      <ApprovalTimeline :logs="detail.logs" />
    </el-card>

    <div v-if="canHandle && isPending" class="actions">
      <el-button v-if="canApproveAndCreatePerson" type="success" :disabled="!allRequiredVerified" :loading="deciding" @click="openCreatePersonDialog">
        通过并建档
      </el-button>
      <el-button v-else type="success" :disabled="!allRequiredVerified" :loading="deciding" @click="approve">
        审批通过
      </el-button>
      <el-button type="danger" plain :loading="deciding" @click="reject">审批驳回</el-button>
      <span v-if="!allRequiredVerified" class="hint">尚未满足该业务类型要求的全部核验材料。</span>
    </div>

    <el-dialog v-model="createPersonVisible" title="通过并建立人口档案" width="680px" destroy-on-close>
      <el-alert type="info" :closable="false" show-icon title="确认后将同时完成审批并建立人口基础档案，操作不可拆分。" />
      <el-form class="create-person-form" label-width="112px">
        <el-form-item label="姓名" required><el-input v-model="personForm.name" maxlength="50" /></el-form-item>
        <el-form-item label="性别" required><el-radio-group v-model="personForm.gender"><el-radio value="M">男</el-radio><el-radio value="F">女</el-radio></el-radio-group></el-form-item>
        <el-form-item label="身份证号" required><el-input v-model="personForm.idCard" maxlength="18" /></el-form-item>
        <el-form-item label="出生日期" required><el-date-picker v-model="personForm.birthDate" value-format="YYYY-MM-DD" type="date" style="width:100%" /></el-form-item>
        <el-form-item label="民族"><el-input v-model="personForm.ethnicity" maxlength="30" /></el-form-item>
        <el-form-item label="联系电话"><el-input v-model="personForm.phone" maxlength="20" /></el-form-item>
        <el-form-item label="现住址"><el-input v-model="personForm.currentAddress" maxlength="255" /></el-form-item>
        <el-form-item label="身份证影印本" required>
          <el-button plain @click="scannerVisible = true">上传并识别身份证</el-button>
          <span v-if="personForm.idCardImageId" class="image-ready">已关联影印本 #{{ personForm.idCardImageId }}</span>
        </el-form-item>
        <el-form-item label="审批意见"><el-input v-model="personForm.comment" type="textarea" maxlength="500" show-word-limit /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createPersonVisible = false">取消</el-button>
        <el-button type="success" :loading="deciding" @click="handleApproveAndCreate">确认通过并建档</el-button>
      </template>
    </el-dialog>
    <IdCardScannerDialog v-model:visible="scannerVisible" @recognized="applyRecognizedIdCard" @skipped="applyRecognizedIdCard" />
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import StatusTag from '../../components/common/StatusTag.vue'
import MaterialList from '../../components/business/MaterialList.vue'
import ApprovalTimeline from '../../components/business/ApprovalTimeline.vue'
import ProfessionalFieldsPanel from '../../components/business/ProfessionalFieldsPanel.vue'
import MigrationDetailPanel from '../migrations/components/MigrationDetailPanel.vue'
import FloatingResidenceDetailPanel from '../floating/components/FloatingResidenceDetailPanel.vue'
import IdCardScannerDialog from '../persons/components/IdCardScannerDialog.vue'
import { approveAndCreatePerson, approveApproval, getApprovalDetail, rejectApproval } from '../../api/approvals'
import { getPersonById } from '../../api/persons'
import { normalizePerson } from '../../adapters/person'
import { getMigrationRecord } from '../../adapters/migration'
import { BUSINESS_TYPE_LABEL } from '../../constants/application'
import { PERMISSIONS } from '../../constants/permissions'
import { getApplicationBusinessHandler } from '../../features/applications/handlers'
import { useUserStore } from '../../stores/user'
import { getApiErrorMessage, isApiConflict } from '../../utils/apiError'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const detail = ref(null)
/** 单一专业详情 */
const professionalDetail = ref(null)
const migrationPerson = ref(null)
const loading = ref(false)
const deciding = ref(false)
const createPersonVisible = ref(false)
const scannerVisible = ref(false)
const personForm = reactive({ name: '', gender: 'M', idCard: '', birthDate: '', ethnicity: '汉族', phone: '', currentAddress: '', idCardImageId: null, comment: '' })

const approvalId = computed(() => route.params.approvalId)
const businessType = computed(() => detail.value?.application?.businessType)
const handler = computed(() => getApplicationBusinessHandler(businessType.value))
const handlerFamily = computed(() => handler.value?.family || '')

const displayDetail = computed(() => {
  if (!handler.value || !professionalDetail.value) return null
  return handler.value.getDisplayDetail(professionalDetail.value)
})

const subjectDetail = computed(() => {
  if (!handler.value || !professionalDetail.value) return null
  return handler.value.getSubject(professionalDetail.value)
})

const isPending = computed(() => detail.value?.approval?.status === 'PENDING')
const canHandle = computed(() => userStore.hasPermission(PERMISSIONS.APPROVAL_HANDLE))
const registrationType = computed(() => {
  const remark = detail.value?.application?.remark || ''
  const match = remark.match(/登记类型=([A-Z_]+)/)
  return match?.[1] || ''
})
const canApproveAndCreatePerson = computed(() => ['RELEASED_RESTORE', 'VETERAN_RESTORE'].includes(registrationType.value))

const allRequiredVerified = computed(() => {
  const materials = detail.value?.materials || []
  const currentHandler = handler.value

  if (currentHandler && professionalDetail.value) {
    return currentHandler.hasVerifiedMaterials({
      businessType: businessType.value,
      detail: professionalDetail.value,
      materials
    })
  }

  // fallback：有 requiredFlag 且全部 VERIFIED
  const required = materials.filter((item) => item.requiredFlag)
  return required.length > 0 && required.every((item) => item.verifyStatus === 'VERIFIED')
})

const genericDetailFields = computed(() => {
  if (!handler.value || !professionalDetail.value) return []
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
    detail.value = await getApprovalDetail(approvalId.value)
    professionalDetail.value = null
    migrationPerson.value = null

    const bt = detail.value.application?.businessType
    const currentHandler = getApplicationBusinessHandler(bt)

    if (currentHandler) {
      const rawDetail = await currentHandler.loadDetail(
        detail.value.application.applicationId,
        bt
      )
      const normalized = typeof currentHandler.normalizeDetail === 'function'
        ? currentHandler.normalizeDetail(rawDetail)
        : rawDetail
      professionalDetail.value = normalized

      if (currentHandler.family === 'migration') {
        await loadMigrationPerson(getMigrationRecord(normalized))
      }
    }
  } catch (error) {
    ElMessage.error(getApiErrorMessage(error, '加载审批详情失败'))
  } finally {
    loading.value = false
  }
}

async function decide(action) {
  const rejecting = action === 'reject'
  const { value } = await ElMessageBox.prompt(
    rejecting ? '请输入驳回意见' : '可填写审批意见',
    rejecting ? '审批驳回' : '审批通过',
    {
      inputPattern: rejecting ? /\S+/ : undefined,
      inputErrorMessage: '驳回意见不能为空',
      confirmButtonText: '确认提交'
    }
  )
  deciding.value = true
  try {
    const payload = { comment: value || '', version: detail.value.approval.version }
    if (rejecting) await rejectApproval(approvalId.value, payload)
    else await approveApproval(approvalId.value, payload)
    ElMessage.success(rejecting ? '已驳回申请' : '已通过申请，等待业务执行')
    await load()
  } catch (error) {
    if (isApiConflict(error)) await load()
    ElMessage.error(getApiErrorMessage(error, '审批处理失败'))
  } finally {
    deciding.value = false
  }
}

function approve() { decide('approve') }
function reject() { decide('reject') }

function openCreatePersonDialog() {
  const title = detail.value?.application?.title || ''
  const separator = title.includes('：') ? '：' : ':'
  Object.assign(personForm, {
    name: title.includes(separator) ? title.split(separator).slice(1).join(separator).trim() : '',
    gender: 'M', idCard: '', birthDate: '', ethnicity: '汉族', phone: '', currentAddress: '', idCardImageId: null, comment: ''
  })
  createPersonVisible.value = true
}

function applyRecognizedIdCard(result) {
  if (!result?.imageId) return
  personForm.idCardImageId = result.imageId
  if (result.ocrStatus === 'SUCCESS') {
    if (result.ocrName) personForm.name = result.ocrName
    if (result.ocrIdcardFull) personForm.idCard = result.ocrIdcardFull
    if (result.ocrBirthDate) personForm.birthDate = String(result.ocrBirthDate).slice(0, 10)
    if (result.ocrGender === 'M' || result.ocrGender === 'F') personForm.gender = result.ocrGender
    if (result.ocrEthnicity) personForm.ethnicity = result.ocrEthnicity
    if (result.ocrAddress) personForm.currentAddress = result.ocrAddress
  }
}

async function handleApproveAndCreate() {
  if (!personForm.name || !personForm.idCard || !personForm.birthDate || !personForm.idCardImageId) {
    ElMessage.error('请完整填写人员信息并上传身份证影印本')
    return
  }
  deciding.value = true
  try {
    await approveAndCreatePerson(approvalId.value, {
      comment: personForm.comment || '',
      version: detail.value.approval.version,
      person: {
        name: personForm.name,
        gender: personForm.gender,
        idCard: personForm.idCard,
        birthDate: personForm.birthDate,
        ethnicity: personForm.ethnicity,
        phone: personForm.phone,
        currentAddress: personForm.currentAddress,
        status: '正常',
        idCardImageId: personForm.idCardImageId
      }
    })
    ElMessage.success('审批通过，人口档案已建立')
    createPersonVisible.value = false
    await load()
  } catch (error) {
    if (isApiConflict(error)) await load()
    ElMessage.error(getApiErrorMessage(error, '通过并建档失败'))
  } finally {
    deciding.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.page-container { display: flex; flex-direction: column; gap: 16px; }
.page-header, .card-header, .actions { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.page-header h1 { margin: 0 0 8px; }
.subtitle, .hint { margin: 0; color: var(--el-text-color-secondary); font-size: 13px; }
.actions { justify-content: flex-start; flex-wrap: wrap; }
.create-person-form { margin-top: 18px; }
.image-ready { margin-left: 10px; color: var(--el-color-success); font-size: 13px; }
</style>
