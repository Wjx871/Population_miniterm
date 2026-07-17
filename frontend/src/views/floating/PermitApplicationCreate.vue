<template>
  <div class="page-container" v-loading="loading">
    <div class="page-header">
      <div><h1>{{ pageTitle }}</h1><p class="subtitle">课程模拟系统，不作为真实政务证件。</p></div>
      <el-button @click="goBack">返回</el-button>
    </div>

    <!-- 首次申领：选择流动登记 -->
    <el-card v-if="applyType === 'FIRST_ISSUE' && !isEdit && !showForm" shadow="never">
      <template #header>选择有效流动登记</template>
      <el-form label-width="100px">
        <el-form-item label="流动登记">
          <FloatingSelect v-model="selectedFloatingId" @select="onFloatingSelect" />
        </el-form-item>
      </el-form>
      <div style="text-align:right;margin-top:16px">
        <el-button type="primary" :disabled="!selectedFloatingId" @click="startFirstIssue">下一步</el-button>
      </div>
    </el-card>

    <!-- 申请表单 -->
    <el-card v-if="showForm" shadow="never">
      <template #header>{{ applyType === 'FIRST_ISSUE' ? '首次申领' : (applyType === 'ENDORSEMENT' ? '签注申请' : '注销申请') }}</template>

      <el-descriptions v-if="applyType === 'FIRST_ISSUE'" :column="2" border style="margin-bottom:16px">
        <el-descriptions-item label="人员">{{ floatingInfo?.personName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="登记编号">{{ floatingInfo?.registrationNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="当前区域">{{ floatingInfo?.currentRegionCode || '-' }}</el-descriptions-item>
        <el-descriptions-item label="当前地址">{{ floatingInfo?.currentAddress || '-' }}</el-descriptions-item>
        <el-descriptions-item label="到达日期">{{ floatingInfo?.arrivalDate || '-' }}</el-descriptions-item>
        <el-descriptions-item label="资格日期">{{ floatingInfo?.eligibleFromDate || '-' }}</el-descriptions-item>
      </el-descriptions>

      <el-descriptions v-if="(applyType === 'ENDORSEMENT' || applyType === 'CANCELLATION') && permitInfo" :column="2" border style="margin-bottom:16px">
        <el-descriptions-item label="证件编号"><SensitiveText :value="permitInfo.permitNo" kind="text" /></el-descriptions-item>
        <el-descriptions-item label="持证人">{{ permitInfo.personName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="有效期" :span="2">
          {{ permitInfo.validFrom || '-' }} ~ {{ permitInfo.validUntil || '-' }}
        </el-descriptions-item>
        <el-descriptions-item v-if="applyType === 'ENDORSEMENT'" label="上次签注">{{ permitInfo.lastEndorsedAt || '-' }}</el-descriptions-item>
      </el-descriptions>

      <el-alert type="warning" :closable="false" show-icon style="margin-bottom:16px">
        <template #title>课程模拟系统生成，不作为真实政务证件。</template>
      </el-alert>

      <el-form ref="formRef" :model="form" :rules="formRules" :disabled="isFormReadOnly" label-width="120px">
        <template v-if="applyType !== 'CANCELLATION'">
          <el-form-item label="居住依据" prop="residenceBasisCode">
            <el-select v-model="form.residenceBasisCode" placeholder="请选择" style="width:100%">
              <el-option v-for="(label, value) in RESIDENCE_BASIS" :key="value" :label="label" :value="value" />
            </el-select>
          </el-form-item>
          <el-form-item label="申请开始日期">
            <el-date-picker v-model="form.requestedValidFrom" type="date" placeholder="选填" value-format="YYYY-MM-DD" style="width:100%" />
          </el-form-item>
          <el-form-item label="申请结束日期">
            <el-date-picker v-model="form.requestedValidUntil" type="date" placeholder="选填" value-format="YYYY-MM-DD" style="width:100%" />
          </el-form-item>
        </template>
        <el-form-item label="标题" prop="title">
          <el-input v-model="form.title" maxlength="200" placeholder="必填，简要描述申请事项" />
        </el-form-item>
        <el-form-item label="原因" prop="reason">
          <el-input v-model="form.reason" type="textarea" :rows="2" placeholder="必填，说明申请原因" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="选填" />
        </el-form-item>
      </el-form>

      <div style="text-align:right;margin-top:16px;display:flex;justify-content:space-between">
        <el-button @click="goBack">返回列表</el-button>
        <el-button type="primary" :loading="saving" :disabled="isFormReadOnly || !hasValidVersion" @click="saveApplication">{{ isEdit ? '更新草稿' : '保存并继续' }}</el-button>
      </div>
    </el-card>

    <!-- 材料上传 -->
    <el-card v-if="showForm && applicationId" shadow="never">
      <template #header>申请材料</template>
      <el-alert type="info" :closable="false" style="margin-bottom:16px">{{ materialRuleText }}</el-alert>
      <MaterialUploader v-if="canUpload && !isFormReadOnly" :application-id="applicationId" :material-options="materialOptions" @uploaded="loadMaterials" />
      <MaterialList :materials="materials" :can-delete="canEdit && !isFormReadOnly" @changed="loadMaterials" />
      <div style="text-align:right;margin-top:16px">
        <el-button type="primary" :loading="submitting" :disabled="!materialsReady || isFormReadOnly" @click="submit">提交申请</el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import FloatingSelect from '../../components/business/FloatingSelect.vue'
import DictionarySelect from '../../components/business/DictionarySelect.vue'
import MaterialUploader from '../../components/business/MaterialUploader.vue'
import MaterialList from '../../components/business/MaterialList.vue'
import SensitiveText from '../../components/common/SensitiveText.vue'
import {
  createPermitFirstIssueApplication, createPermitEndorsementApplication, createPermitCancellationApplication,
  updatePermitApplication, getPermitApplicationDetail, getFloatingPopulationById, getResidencePermitById
} from '../../api/floatingResidence'
import { submitApplication } from '../../api/applications'
import { getMaterials } from '../../api/materials'
import { normalizePermitProfessional } from '../../adapters/residencePermit'
import { normalizeFloatingPopulation } from '../../adapters/floating'
import { normalizeResidencePermit } from '../../adapters/residencePermit'
import { PERMIT_APPLY_TYPE, getPermitMaterialOptions, getPermitMaterialRuleText, hasUploadedPermitMaterials } from '../../constants/floatingResidence'
import { PERMISSIONS } from '../../constants/permissions'
import { useUserStore } from '../../stores/user'
import { RESIDENCE_BASIS } from '../../constants/floatingResidence'
import { getApiErrorMessage, isApiConflict } from '../../utils/apiError'
import { useProfessionalDraftState } from '../../composables/useProfessionalDraftState'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const {
  applicationId,
  professionalVersion,
  isEdit,
  isReadOnly: isFormReadOnly,
  hasValidVersion,
  applyDetailMeta,
  markCreated
} = useProfessionalDraftState()

const loading = ref(false)
const saving = ref(false)
const submitting = ref(false)
const showForm = ref(false)
const materials = ref([])
const floatingInfo = ref(null)
const permitInfo = ref(null)
const selectedFloatingId = ref(null)
const formRef = ref(null)

// 从路由中推断applyType
const applyType = computed(() => {
  if (route.path.includes('/first-issue')) return 'FIRST_ISSUE'
  if (route.path.includes('/endorsement')) return 'ENDORSEMENT'
  if (route.path.includes('/cancellation')) return 'CANCELLATION'
  return 'FIRST_ISSUE'
})

const pageTitle = computed(() => PERMIT_APPLY_TYPE[applyType.value] + '申请')
const canUpload = computed(() => userStore.hasPermission(PERMISSIONS.MATERIAL_UPLOAD))
const canEdit = computed(() => userStore.hasPermission(PERMISSIONS.MATERIAL_DELETE))
const materialOptions = computed(() => getPermitMaterialOptions(applyType.value, floatingInfo.value?.residenceReasonCode))
const materialRuleText = computed(() => getPermitMaterialRuleText(applyType.value, floatingInfo.value?.residenceReasonCode))
const materialsReady = computed(() => hasUploadedPermitMaterials(materials.value, applyType.value, floatingInfo.value?.residenceReasonCode))

const form = reactive({
  residenceBasisCode: '',
  title: '',
  reason: '',
  remark: '',
  requestedValidFrom: '',
  requestedValidUntil: ''
})

const formRules = computed(() => {
  const rules = {
    title: [{ required: true, message: '请输入标题', trigger: 'blur' }],
    reason: [{ required: true, message: '请输入原因', trigger: 'blur' }]
  }
  if (applyType.value !== 'CANCELLATION') {
    rules.residenceBasisCode = [{ required: true, message: '请选择居住依据', trigger: 'change' }]
    rules.requestedValidUntil = [{
      validator: (_rule, value, callback) => {
        if (value && form.requestedValidFrom && value < form.requestedValidFrom) callback(new Error('结束日期不能早于开始日期'))
        else callback()
      }, trigger: 'change'
    }]
  }
  return rules
})

function onFloatingSelect(item) { floatingInfo.value = item }

async function startFirstIssue() {
  if (!selectedFloatingId.value) return
  try {
    floatingInfo.value = normalizeFloatingPopulation(await getFloatingPopulationById(selectedFloatingId.value))
    showForm.value = true
  } catch (error) {
    ElMessage.error(getApiErrorMessage(error, '加载流动登记信息失败'))
  }
}

async function loadMaterials() {
  if (!applicationId.value) return
  try { materials.value = await getMaterials(applicationId.value) || [] } catch { materials.value = [] }
}

async function loadApplicationForEdit() {
  if (!applicationId.value) return false
  loading.value = true
  try {
    const detail = normalizePermitProfessional(await getPermitApplicationDetail(applicationId.value))
    if (!detail?.professional) { ElMessage.error('无法加载申请信息'); return }
    const p = detail.professional
    form.residenceBasisCode = p.residenceBasisCode || ''
    form.title = detail.application?.title || ''
    form.reason = detail.application?.reason || ''
    form.remark = detail.application?.remark || ''
    form.requestedValidFrom = p.requestedValidFrom || ''
    form.requestedValidUntil = p.requestedValidUntil || ''
    
    applyDetailMeta(detail)
    if (p.floatingId) {
      try { floatingInfo.value = normalizeFloatingPopulation(await getFloatingPopulationById(p.floatingId)) } catch { /* non-blocking */ }
    }
    if (p.permitId) {
      try { permitInfo.value = normalizeResidencePermit(await getResidencePermitById(p.permitId)) } catch { /* non-blocking */ }
    }
    showForm.value = true
    await loadMaterials()
  } catch (error) {
    ElMessage.error(getApiErrorMessage(error, '加载申请失败'))
  } finally { loading.value = false }
}

async function saveApplication() {
  const valid = await new Promise((resolve) => formRef.value?.validate((v) => resolve(v)))
  if (!valid) return
  saving.value = true
  try {
    const base = { title: form.title || '', reason: form.reason || '', remark: form.remark || '' }
    if (isEdit.value) {
      if (!hasValidVersion.value) {
        ElMessage.error('专业申请缺少版本号，无法更新，请重新加载')
        saving.value = false
        return
      }
      await updatePermitApplication(applicationId.value, {
        ...base,
        residenceBasisCode: form.residenceBasisCode,
        requestedValidFrom: form.requestedValidFrom || null,
        requestedValidUntil: form.requestedValidUntil || null,
        version: professionalVersion.value
      })
      ElMessage.success('草稿已更新')
      await loadMaterials()
    } else {
      const payload = applyType.value === 'FIRST_ISSUE' ? { ...base, floatingId: selectedFloatingId.value || floatingInfo.value?.floatingId, residenceBasisCode: form.residenceBasisCode, requestedValidFrom: form.requestedValidFrom || null, requestedValidUntil: form.requestedValidUntil || null }
        : applyType.value === 'ENDORSEMENT' ? { ...base, residenceBasisCode: form.residenceBasisCode, requestedValidFrom: form.requestedValidFrom || null, requestedValidUntil: form.requestedValidUntil || null }
        : base
      let result
      if (applyType.value === 'FIRST_ISSUE') result = await createPermitFirstIssueApplication(payload)
      else if (applyType.value === 'ENDORSEMENT') result = await createPermitEndorsementApplication(route.params.permitId, payload)
      else result = await createPermitCancellationApplication(route.params.permitId, payload)
      const createdApplicationId = result?.applicationId ?? result?.id
      if (!createdApplicationId) throw new Error('创建申请成功，但响应中缺少申请编号，请刷新列表确认')
      markCreated(createdApplicationId)
      ElMessage.success('草稿已创建')
      await loadApplicationForEdit()
    }
  } catch (error) {
    if (isApiConflict(error) && applicationId.value) {
      await loadApplicationForEdit()
      ElMessage.warning('版本冲突，数据已刷新。')
    } else {
      ElMessage.error(getApiErrorMessage(error, '保存失败'))
    }
  } finally { saving.value = false }
}

async function submit() {
  try { await ElMessageBox.confirm('提交后不能继续编辑草稿，确认提交吗？', '提交申请', { type: 'warning' }) } catch { return }
  submitting.value = true
  try {
    await submitApplication(applicationId.value)
    ElMessage.success('申请已提交')
    router.push('/applications')
  } catch (error) {
    if (isApiConflict(error)) await loadApplicationForEdit()
    ElMessage.error(getApiErrorMessage(error, '提交失败'))
  } finally { submitting.value = false }
}


function goBack() {
  if (route.path.includes('/first-issue')) router.push('/floating-population')
  else router.push('/residence-permits')
}

onMounted(async () => {
  const appId = route.query.applicationId
  // 从查询参数获取浮动ID（首次申领的快捷入口）
  if (route.query.floatingId) {
    selectedFloatingId.value = route.query.floatingId
  }
  // 从路由参数获取permitId（签注/注销）
  if (route.params.permitId && (applyType.value === 'ENDORSEMENT' || applyType.value === 'CANCELLATION')) {
    try { permitInfo.value = normalizeResidencePermit(await getResidencePermitById(route.params.permitId)) } catch { /* non-blocking */ }
    showForm.value = true
  }
  if (selectedFloatingId.value && applyType.value === 'FIRST_ISSUE' && !appId) {
    await startFirstIssue()
  }
  if (appId) {
    applicationId.value = appId
    await loadApplicationForEdit()
  }
})
</script>

<style scoped>
.page-container{display:flex;flex-direction:column;gap:16px}
.page-header{display:flex;justify-content:space-between;align-items:end}
.page-header h1{margin:0 0 8px}
.subtitle{margin:0;color:var(--el-text-color-secondary)}
</style>
