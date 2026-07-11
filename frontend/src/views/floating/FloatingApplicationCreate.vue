<template>
  <div class="page-container" v-loading="loading">
    <div class="page-header">
      <div><h1>{{ isEdit ? '编辑流动登记申请' : '流动人口登记申请' }}</h1><p class="subtitle">所有信息仅用于课程模拟演示。</p></div>
      <el-button @click="backToList">返回列表</el-button>
    </div>

    <el-steps :active="activeStep" finish-status="success" align-center style="margin-bottom: 24px">
      <el-step title="人员信息" />
      <el-step title="居住信息" />
      <el-step title="材料上传" />
      <el-step title="确认提交" />
    </el-steps>

    <!-- 步骤1：人员信息 -->
    <el-card v-show="activeStep === 0" shadow="never">
      <template #header>1. 人员与来源信息</template>
      <el-form ref="step1Ref" :model="form" :rules="step1Rules" label-width="120px">
        <el-form-item label="选择人员" prop="personId">
          <PersonSelect v-model="form.personId" :disabled="isEdit || isFormReadOnly" @select="onPersonSelect" />
        </el-form-item>
        <el-form-item label="来源区划" prop="sourceRegionCode">
          <el-input v-model="form.sourceRegionCode" :disabled="isFormReadOnly" maxlength="20" placeholder="6~20位数字" />
        </el-form-item>
        <el-form-item label="来源地址">
          <el-input v-model="form.sourceAddress" :disabled="isFormReadOnly" maxlength="255" placeholder="选填" />
        </el-form-item>
      </el-form>
      <div style="text-align:right;margin-top:16px"><el-button type="primary" @click="nextStep" :disabled="isFormReadOnly">下一步</el-button></div>
    </el-card>

    <!-- 步骤2：居住信息 -->
    <el-card v-show="activeStep === 1" shadow="never">
      <template #header>2. 当前居住信息</template>
      <el-form ref="step2Ref" :model="form" :rules="step2Rules" label-width="120px">
        <el-form-item label="当前区划" prop="currentRegionCode">
          <el-input v-model="form.currentRegionCode" maxlength="20" placeholder="6~20位数字" />
        </el-form-item>
        <el-form-item label="当前地址" prop="currentAddress">
          <el-input v-model="form.currentAddress" maxlength="255" show-word-limit placeholder="必填，最大255字符" />
        </el-form-item>
        <el-form-item label="居住事由" prop="residenceReasonCode">
          <el-select v-model="form.residenceReasonCode" placeholder="请选择" style="width:100%" @change="onReasonChange">
            <el-option v-for="(label, code) in RESIDENCE_REASON" :key="code" :label="label" :value="code" />
          </el-select>
        </el-form-item>
        <el-form-item label="居住证明类型" prop="residenceProofType">
          <el-select v-model="form.residenceProofType" placeholder="请选择" style="width:100%">
            <el-option v-for="(label, code) in RESIDENCE_PROOF_TYPE" :key="code" :label="label" :value="code" />
          </el-select>
        </el-form-item>
        <el-form-item label="到达日期" prop="arrivalDate">
          <el-date-picker v-model="form.arrivalDate" type="date" placeholder="选择日期" value-format="YYYY-MM-DD" style="width:100%" />
        </el-form-item>
        <el-form-item label="计划离开日期" prop="plannedLeaveDate">
          <el-date-picker v-model="form.plannedLeaveDate" type="date" placeholder="选填" value-format="YYYY-MM-DD" style="width:100%" />
        </el-form-item>
        <el-form-item label="联系电话">
          <el-input v-model="form.applicantPhone" maxlength="20" placeholder="选填，7~20位数字/加号/空格/横线" />
        </el-form-item>
        <el-form-item label="申请标题" prop="title">
          <el-input v-model="form.title" maxlength="200" placeholder="必填，简要描述申请事项" />
        </el-form-item>
        <el-form-item label="申请原因" prop="reason">
          <el-input v-model="form.reason" type="textarea" :rows="2" placeholder="必填，说明申请原因" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="选填" />
        </el-form-item>
      </el-form>
      <div style="text-align:right;margin-top:16px;display:flex;justify-content:space-between">
        <el-button @click="activeStep = 0">上一步</el-button>
        <el-button type="primary" :loading="saving" :disabled="isFormReadOnly || !hasValidVersion" @click="saveStep">保存草稿，继续下一步</el-button>
      </div>
    </el-card>

    <!-- 步骤3：材料上传 -->
    <el-card v-show="activeStep === 2" shadow="never">
      <template #header>3. 上传材料</template>
      <el-alert type="info" :closable="false" style="margin-bottom:16px">{{ materialRuleText }}</el-alert>
      <MaterialUploader v-if="applicationId && canUpload && !isFormReadOnly" :application-id="applicationId" :material-options="materialOptions" @uploaded="loadMaterials" />
      <MaterialList :materials="materials" :can-delete="canEdit && !isFormReadOnly" @changed="loadMaterials" />
      <div style="text-align:right;margin-top:16px;display:flex;justify-content:space-between">
        <el-button @click="activeStep = 1" :disabled="isFormReadOnly">上一步</el-button>
        <el-button type="primary" @click="activeStep = 3" :disabled="isFormReadOnly">检查并提交</el-button>
      </div>
    </el-card>

    <!-- 步骤4：确认提交 -->
    <el-card v-show="activeStep === 3" shadow="never">
      <template #header>4. 确认提交</template>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="人员">{{ selectedPersonName }}</el-descriptions-item>
        <el-descriptions-item label="联系电话">{{ form.applicantPhone || '-' }}</el-descriptions-item>
        <el-descriptions-item label="当前区划">{{ form.currentRegionCode }}</el-descriptions-item>
        <el-descriptions-item label="当前地址">{{ form.currentAddress }}</el-descriptions-item>
        <el-descriptions-item label="居住事由">{{ RESIDENCE_REASON[form.residenceReasonCode] }}</el-descriptions-item>
        <el-descriptions-item label="证明类型">{{ RESIDENCE_PROOF_TYPE[form.residenceProofType] }}</el-descriptions-item>
        <el-descriptions-item label="到达日期">{{ form.arrivalDate }}</el-descriptions-item>
        <el-descriptions-item label="计划离开">{{ form.plannedLeaveDate || '-' }}</el-descriptions-item>
      </el-descriptions>
      <el-alert v-if="!materialsReady" type="warning" :closable="false" show-icon style="margin-top: 16px">
        <template #title>材料未完整。所有必需材料须上传后才能提交，材料将在审批阶段进行核验。</template>
      </el-alert>
      <div style="text-align:right;margin-top:16px;display:flex;justify-content:space-between">
        <el-button @click="activeStep = 2">上一步</el-button>
        <el-button type="primary" :loading="submitting" :disabled="!materialsReady || isFormReadOnly" @click="submit">提交申请</el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import PersonSelect from '../../components/business/PersonSelect.vue'
import MaterialUploader from '../../components/business/MaterialUploader.vue'
import MaterialList from '../../components/business/MaterialList.vue'
import { createFloatingApplication, updateFloatingApplication, getFloatingApplicationDetail } from '../../api/floatingResidence'
import { submitApplication } from '../../api/applications'
import { getMaterials } from '../../api/materials'
import { normalizeFloatingProfessional } from '../../adapters/floating'
import { RESIDENCE_REASON, RESIDENCE_PROOF_TYPE, getFloatingMaterialOptions, getFloatingMaterialRuleText, hasUploadedFloatingMaterials } from '../../constants/floatingResidence'
import { PERMISSIONS } from '../../constants/permissions'
import { useUserStore } from '../../stores/user'
import { getApiErrorMessage, isApiConflict } from '../../utils/apiError'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const isEdit = ref(false)
const applicationId = ref(null)
const professionalVersion = ref(null)
const detailAppStatus = ref('DRAFT')
const loading = ref(false)
const saving = ref(false)
const submitting = ref(false)
const activeStep = ref(0)
const materials = ref([])
const selectedPersonName = ref('')
const step1Ref = ref(null)
const step2Ref = ref(null)

const form = reactive({
  personId: null,
  sourceRegionCode: '',
  sourceAddress: '',
  currentRegionCode: '',
  currentAddress: '',
  residenceReasonCode: '',
  residenceProofType: '',
  arrivalDate: '',
  plannedLeaveDate: '',
  applicantPhone: '',
  title: '',
  reason: '',
  remark: ''
})

const step1Rules = {
  personId: [{ required: true, message: '请选择人员', trigger: 'change' }],
  sourceRegionCode: [{ required: true, message: '请输入来源区划', trigger: 'blur' }, { pattern: /^\d{6,20}$/, message: '6~20位数字', trigger: 'blur' }]
}

const step2Rules = {
  currentRegionCode: [{ required: true, message: '请输入当前区划', trigger: 'blur' }, { pattern: /^\d{6,20}$/, message: '6~20位数字', trigger: 'blur' }],
  currentAddress: [{ required: true, message: '请输入当前地址', trigger: 'blur' }],
  residenceReasonCode: [{ required: true, message: '请选择居住事由', trigger: 'change' }],
  residenceProofType: [{ required: true, message: '请选择证明类型', trigger: 'change' }],
  arrivalDate: [{ required: true, message: '请选择到达日期', trigger: 'change' }],
  plannedLeaveDate: [{
    validator: (_rule, value, callback) => {
      if (value && form.arrivalDate && value < form.arrivalDate) callback(new Error('计划离开日期不能早于到达日期'))
      else callback()
    }, trigger: 'change'
  }],
  title: [{ required: true, message: '请输入申请标题', trigger: 'blur' }],
  reason: [{ required: true, message: '请输入申请原因', trigger: 'blur' }]
}

const canUpload = computed(() => userStore.hasPermission(PERMISSIONS.MATERIAL_UPLOAD))
const canEdit = computed(() => userStore.hasPermission(PERMISSIONS.MATERIAL_DELETE))
const isFormReadOnly = computed(() => detailAppStatus.value !== 'DRAFT')
const hasValidVersion = computed(() => Number.isInteger(professionalVersion.value))
const materialOptions = computed(() => getFloatingMaterialOptions(form.residenceReasonCode))
const materialRuleText = computed(() => getFloatingMaterialRuleText(form.residenceReasonCode))
const materialsReady = computed(() => hasUploadedFloatingMaterials(materials.value, form.residenceReasonCode))

function onPersonSelect(person) { selectedPersonName.value = person?.name || ''; form.applicantPhone = person?.phone || '' }
function onReasonChange() { ElMessage.info('居住事由已变更，材料要求可能发生变化') }

async function loadMaterials() {
  if (!applicationId.value) return
  try { materials.value = await getMaterials(applicationId.value) || [] } catch { materials.value = [] }
}

async function loadApplication() {
  loading.value = true
  try {
    const detail = normalizeFloatingProfessional(await getFloatingApplicationDetail(applicationId.value))
    if (!detail?.professional) { ElMessage.error('无法加载申请信息'); return }
    const p = detail.professional
    form.personId = p.personId
    form.sourceRegionCode = p.sourceRegionCode || ''
    form.sourceAddress = p.sourceAddress || ''
    form.currentRegionCode = p.currentRegionCode || ''
    form.currentAddress = p.currentAddress || ''
    form.residenceReasonCode = p.residenceReasonCode || ''
    form.residenceProofType = p.residenceProofType || ''
    form.arrivalDate = p.arrivalDate || ''
    form.plannedLeaveDate = p.plannedLeaveDate || ''
    form.applicantPhone = p.applicantPhone || ''
    form.title = detail.application?.title || ''
    form.reason = detail.application?.reason || ''
    form.remark = detail.application?.remark || ''
    professionalVersion.value = Number.isInteger(p.version) ? p.version : null
    detailAppStatus.value = detail.application?.status || 'DRAFT'
    if (detailAppStatus.value !== 'DRAFT') {
      activeStep.value = 3
    }
    selectedPersonName.value = p.personName || ''
    await loadMaterials()
  } catch (error) {
    ElMessage.error(getApiErrorMessage(error, '加载申请失败'))
  } finally { loading.value = false }
}

function nextStep() {
  step1Ref.value?.validate((valid) => { if (valid) activeStep.value = 1 })
}

async function saveStep() {
  const valid = await new Promise((resolve) => step2Ref.value?.validate((v) => resolve(v)))
  if (!valid) return
  saving.value = true
  try {
    const payload = {
      personId: form.personId,
      sourceRegionCode: form.sourceRegionCode,
      sourceAddress: form.sourceAddress,
      currentRegionCode: form.currentRegionCode,
      currentAddress: form.currentAddress,
      residenceReasonCode: form.residenceReasonCode,
      residenceProofType: form.residenceProofType,
      arrivalDate: form.arrivalDate,
      plannedLeaveDate: form.plannedLeaveDate || null,
      applicantPhone: form.applicantPhone || '',
      title: form.title || '',
      reason: form.reason || '',
      remark: form.remark || ''
    }
    if (isEdit.value) {
      await updateFloatingApplication(applicationId.value, { ...payload, version: professionalVersion.value })
      ElMessage.success('草稿已更新')
      await loadApplication()
    } else {
      const result = await createFloatingApplication(payload)
      applicationId.value = result?.applicationId || result?.id
      isEdit.value = true
      ElMessage.success('草稿已创建')
    }
    await loadApplication()
    activeStep.value = 2
  } catch (error) {
    if (isApiConflict(error)) { await loadApplication(); ElMessage.warning('版本冲突，数据已刷新，请检查后重新编辑。') }
    else ElMessage.error(getApiErrorMessage(error, '保存失败'))
  } finally { saving.value = false }
}

async function submit() {
  try {
    await ElMessageBox.confirm('提交后不能继续编辑草稿，确认提交吗？', '提交申请', { type: 'warning' })
  } catch { return }
  submitting.value = true
  try {
    await submitApplication(applicationId.value)
    ElMessage.success('申请已提交')
    router.push('/applications')
  } catch (error) {
    if (isApiConflict(error)) await loadApplication()
    ElMessage.error(getApiErrorMessage(error, '提交失败'))
  } finally { submitting.value = false }
}

function backToList() { router.push('/floating-population') }

onMounted(async () => {
  const appId = route.query.applicationId
  if (appId) {
    isEdit.value = true
    applicationId.value = appId
    await loadApplication()
    activeStep.value = 2
  }
})
</script>

<style scoped>
.page-container{display:flex;flex-direction:column;gap:16px}
.page-header{display:flex;justify-content:space-between;align-items:end}
.page-header h1{margin:0 0 8px}
.subtitle{margin:0;color:var(--el-text-color-secondary)}
</style>
