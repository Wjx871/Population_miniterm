<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <h1>{{ pageTitle }}</h1>
        <p class="subtitle">先保存草稿，再上传材料并提交审批；提交后仅可查看或撤回。</p>
      </div>
      <el-button @click="router.back()">返回</el-button>
    </div>

    <el-steps :active="step" finish-status="success" simple>
      <el-step title="业务信息" />
      <el-step title="申请材料" />
      <el-step title="提交审批" />
    </el-steps>

    <el-card shadow="never" class="form-card">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="120px" @submit.prevent>
        <el-form-item label="对象类型" prop="objectType">
          <el-radio-group v-model="form.objectType" :disabled="isReadonly || Boolean(applicationId)">
            <el-radio-button
              v-if="canCreatePerson || (Boolean(applicationId) && form.objectType === 'PERSON')"
              label="PERSON"
            >
              人员注销
            </el-radio-button>
            <el-radio-button
              v-if="canCreateHousehold || (Boolean(applicationId) && form.objectType === 'HOUSEHOLD')"
              label="HOUSEHOLD"
            >
              家庭户销户
            </el-radio-button>
          </el-radio-group>
        </el-form-item>

        <el-form-item label="申请标题" prop="title">
          <el-input v-model.trim="form.title" maxlength="200" show-word-limit :disabled="isReadonly" />
        </el-form-item>

        <el-form-item v-if="isPerson" label="办理人员" prop="personId">
          <PersonSelect v-model="form.personId" :disabled="isReadonly" />
        </el-form-item>

        <el-form-item v-else label="目标家庭户" prop="householdId">
          <HouseholdSelect v-model="form.householdId" :disabled="isReadonly" />
        </el-form-item>

        <el-form-item label="注销原因" prop="cancelReasonCode">
          <el-select v-model="form.cancelReasonCode" :disabled="isReadonly" style="width: 100%">
            <el-option
              v-for="item in reasonOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="原因说明">
          <el-input
            v-model.trim="form.cancelReasonDetail"
            type="textarea"
            :rows="2"
            maxlength="500"
            show-word-limit
            :disabled="isReadonly"
          />
        </el-form-item>

        <el-form-item label="事件日期" prop="eventDate">
          <el-date-picker
            v-model="form.eventDate"
            type="date"
            value-format="YYYY-MM-DD"
            :disabled="isReadonly"
          />
        </el-form-item>

        <el-form-item v-if="isPerson" label="新户主">
          <PersonSelect v-model="form.newHeadPersonId" :disabled="isReadonly" />
          <div class="form-tip">仅当注销对象为户主且户内仍有其他有效成员时，按后端校验指定新户主；前端不自行猜测。</div>
        </el-form-item>

        <el-form-item label="申请说明" prop="reason">
          <el-input v-model.trim="form.reason" type="textarea" :rows="3" :disabled="isReadonly" />
        </el-form-item>

        <el-form-item label="备注">
          <el-input
            v-model.trim="form.remark"
            type="textarea"
            :rows="2"
            maxlength="500"
            show-word-limit
            :disabled="isReadonly"
          />
        </el-form-item>

        <el-alert
          v-if="executionRestriction"
          type="warning"
          :closable="false"
          show-icon
          :title="executionRestriction"
          style="margin-bottom: 16px"
        />

        <el-form-item>
          <el-button
            v-if="!isReadonly && canSaveCurrentObject"
            type="primary"
            :loading="saving"
            @click="saveDraft"
          >
            保存草稿
          </el-button>
          <el-button @click="router.push('/cancellations')">返回列表</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <template v-if="applicationId">
      <MaterialUploader
        v-if="!isReadonly && canUpload"
        :application-id="applicationId"
        :material-options="materialOptions"
        :material-rule-text="materialRuleText"
        @uploaded="refreshDetail"
      />
      <el-card shadow="never">
        <template #header>已上传材料</template>
        <MaterialList
          :materials="detail?.materials || []"
          :can-delete="!isReadonly && canDelete"
          @changed="refreshDetail"
        />
      </el-card>
      <ApplicationActionBar
        :application="detail?.application"
        :loading="submitting"
        :can-submit="canSubmitCancellation"
        @submit="submit"
        @withdraw="withdraw"
        @cancel="cancelDraft"
      />
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import PersonSelect from '../../components/business/PersonSelect.vue'
import HouseholdSelect from '../../components/business/HouseholdSelect.vue'
import MaterialUploader from '../../components/business/MaterialUploader.vue'
import MaterialList from '../../components/business/MaterialList.vue'
import ApplicationActionBar from '../../components/business/ApplicationActionBar.vue'
import {
  createHouseholdCancellation,
  createPersonCancellation,
  getCancellationApplicationDetail,
  updateHouseholdCancellation,
  updatePersonCancellation
} from '../../api/cancellations'
import { cancelDraftApplication, submitApplication, withdrawApplication } from '../../api/applications'
import {
  toCreateHouseholdCancellationPayload,
  toCreatePersonCancellationPayload,
  toUpdateHouseholdCancellationPayload,
  toUpdatePersonCancellationPayload,
  normalizeCancellationProfessional
} from '../../adapters/cancellation'
import {
  CANCEL_OBJECT_TYPE,
  getCancellationMaterialOptions,
  getCancellationMaterialRuleText,
  getCancellationReasonOptions
} from '../../constants/cancellation'
import { PERMISSIONS } from '../../constants/permissions'
import { useUserStore } from '../../stores/user'
import { getApiErrorMessage, isApiConflict } from '../../utils/apiError'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const formRef = ref()
const applicationId = ref(route.query.applicationId || null)
const detail = ref(null)
const saving = ref(false)
const submitting = ref(false)
const executionRestriction = ref('')

const canCreatePerson = computed(() => userStore.hasPermission(PERMISSIONS.CANCELLATION_PERSON_CREATE))
const canCreateHousehold = computed(() => userStore.hasPermission(PERMISSIONS.CANCELLATION_HOUSEHOLD_CREATE))
const canEditApplication = computed(() => userStore.hasPermission(PERMISSIONS.APPLICATION_EDIT))

/** 按权限与 query 确定默认对象类型，禁止无写权限时落到 PERSON */
function resolveInitialObjectType() {
  const requested = route.query.objectType
  if (requested === 'HOUSEHOLD' && canCreateHousehold.value) {
    return CANCEL_OBJECT_TYPE.HOUSEHOLD
  }
  if (requested === 'PERSON' && canCreatePerson.value) {
    return CANCEL_OBJECT_TYPE.PERSON
  }
  if (canCreatePerson.value) return CANCEL_OBJECT_TYPE.PERSON
  if (canCreateHousehold.value) return CANCEL_OBJECT_TYPE.HOUSEHOLD
  return CANCEL_OBJECT_TYPE.PERSON
}

const form = reactive({
  objectType: resolveInitialObjectType(),
  personId: null,
  householdId: null,
  cancelReasonCode: '',
  cancelReasonDetail: '',
  eventDate: '',
  newHeadPersonId: null,
  title: '',
  reason: '',
  remark: '',
  version: null
})

const isPerson = computed(() => form.objectType === CANCEL_OBJECT_TYPE.PERSON)
const isReadonly = computed(() => Boolean(applicationId.value) && detail.value?.application?.status !== 'DRAFT')
const step = computed(() => (!applicationId.value ? 0 : isReadonly.value ? 2 : 1))
const pageTitle = computed(() => (isPerson.value ? '人员注销申请' : '家庭户销户申请'))
const reasonOptions = computed(() => getCancellationReasonOptions(form.objectType))
const materialOptions = computed(() => getCancellationMaterialOptions(form.objectType, form.cancelReasonCode))
const materialRuleText = computed(() => getCancellationMaterialRuleText(form.objectType, form.cancelReasonCode))
const canUpload = computed(() => userStore.hasPermission(PERMISSIONS.MATERIAL_UPLOAD))
const canDelete = computed(() => userStore.hasPermission(PERMISSIONS.MATERIAL_DELETE))

/** 当前对象类型是否可保存：创建需专业 create 权；更新草稿还需 application:edit */
const canSaveCurrentObject = computed(() => {
  const hasCreate = isPerson.value ? canCreatePerson.value : canCreateHousehold.value
  if (!hasCreate) return false
  if (applicationId.value) return canEditApplication.value
  return true
})

const canSubmitCancellation = computed(() => userStore.hasPermission(PERMISSIONS.APPLICATION_SUBMIT))

const rules = computed(() => ({
  objectType: [{ required: true, message: '请选择对象类型', trigger: 'change' }],
  title: [{ required: true, message: '请输入申请标题', trigger: 'blur' }],
  personId: isPerson.value
    ? [{ required: true, message: '请选择办理人员', trigger: 'change' }]
    : [],
  householdId: !isPerson.value
    ? [{ required: true, message: '请选择目标家庭户', trigger: 'change' }]
    : [],
  cancelReasonCode: [{ required: true, message: '请选择注销原因', trigger: 'change' }],
  eventDate: [{ required: true, message: '请选择事件日期', trigger: 'change' }],
  reason: [{ required: true, message: '请输入申请说明', trigger: 'blur' }]
}))

watch(() => form.objectType, () => {
  // 切换对象类型时清空原因，避免跨类型枚举串用
  if (!applicationId.value) {
    form.cancelReasonCode = ''
  }
})

function fillFromDetail(normalized) {
  if (!normalized?.cancellation) return
  const c = normalized.cancellation
  form.objectType = c.cancelObjectType || form.objectType
  form.personId = c.personId || null
  form.householdId = c.householdId || null
  form.cancelReasonCode = c.cancelReasonCode || ''
  form.cancelReasonDetail = c.cancelReasonDetail || ''
  form.eventDate = c.eventDate || ''
  form.version = c.version
  form.title = normalized.application?.title || form.title
  form.reason = normalized.application?.reason || form.reason
  form.remark = normalized.application?.remark || form.remark
  executionRestriction.value = normalized.executionRestriction || ''
}

async function refreshDetail() {
  if (!applicationId.value) return
  try {
    const raw = await getCancellationApplicationDetail(applicationId.value)
    detail.value = normalizeCancellationProfessional(raw)
    fillFromDetail(detail.value)
  } catch (error) {
    ElMessage.error(getApiErrorMessage(error, '加载申请详情失败'))
  }
}

async function saveDraft() {
  if (!canSaveCurrentObject.value) {
    ElMessage.error(isPerson.value ? '无权创建人员注销申请' : '无权创建家庭户销户申请')
    return
  }
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    let result
    if (isPerson.value) {
      const payload = applicationId.value
        ? toUpdatePersonCancellationPayload(form)
        : toCreatePersonCancellationPayload(form)
      result = applicationId.value
        ? await updatePersonCancellation(applicationId.value, payload)
        : await createPersonCancellation(payload)
    } else {
      const payload = applicationId.value
        ? toUpdateHouseholdCancellationPayload(form)
        : toCreateHouseholdCancellationPayload(form)
      result = applicationId.value
        ? await updateHouseholdCancellation(applicationId.value, payload)
        : await createHouseholdCancellation(payload)
    }
    applicationId.value = result.applicationId || applicationId.value
    ElMessage.success('草稿已保存')
    await router.replace({
      query: {
        applicationId: applicationId.value,
        objectType: form.objectType
      }
    })
    await refreshDetail()
  } catch (error) {
    if (isApiConflict(error)) await refreshDetail()
    ElMessage.error(getApiErrorMessage(error, '草稿保存失败'))
  } finally {
    saving.value = false
  }
}

async function submit() {
  await ElMessageBox.confirm('提交后将不能继续编辑草稿，确认提交吗？', '提交申请', { type: 'warning' })
  submitting.value = true
  try {
    await submitApplication(applicationId.value)
    ElMessage.success('申请已提交，等待审批')
    await refreshDetail()
  } catch (error) {
    if (isApiConflict(error)) await refreshDetail()
    ElMessage.error(getApiErrorMessage(error, '提交失败'))
  } finally {
    submitting.value = false
  }
}

async function withdraw() {
  await ElMessageBox.confirm('确认撤回该申请吗？', '撤回申请', { type: 'warning' })
  try {
    await withdrawApplication(applicationId.value)
    ElMessage.success('申请已撤回')
    await refreshDetail()
  } catch (error) {
    if (isApiConflict(error)) await refreshDetail()
    ElMessage.error(getApiErrorMessage(error, '撤回失败'))
  }
}

async function cancelDraft() {
  await ElMessageBox.confirm('确认取消该草稿吗？取消后不能恢复。', '取消草稿', { type: 'warning' })
  try {
    await cancelDraftApplication(applicationId.value)
    ElMessage.success('草稿已取消')
    await router.replace('/cancellations')
  } catch (error) {
    if (isApiConflict(error)) await refreshDetail()
    ElMessage.error(getApiErrorMessage(error, '取消草稿失败'))
  }
}

onMounted(async () => {
  // 新建且无任何创建权限：直接回列表，避免 viewer 误操作
  if (!applicationId.value && !canCreatePerson.value && !canCreateHousehold.value) {
    ElMessage.error('无权创建注销申请')
    await router.replace('/cancellations')
    return
  }
  // 若默认类型与权限不一致（仅家庭户权却落到 PERSON），纠正
  if (!applicationId.value) {
    if (form.objectType === CANCEL_OBJECT_TYPE.PERSON && !canCreatePerson.value && canCreateHousehold.value) {
      form.objectType = CANCEL_OBJECT_TYPE.HOUSEHOLD
    } else if (form.objectType === CANCEL_OBJECT_TYPE.HOUSEHOLD && !canCreateHousehold.value && canCreatePerson.value) {
      form.objectType = CANCEL_OBJECT_TYPE.PERSON
    }
  }
  await refreshDetail()
})
</script>

<style scoped>
.page-container { display: flex; flex-direction: column; gap: 16px; }
.page-header { display: flex; justify-content: space-between; align-items: end; }
.page-header h1 { margin: 0 0 8px; }
.subtitle, .form-tip { color: var(--el-text-color-secondary); font-size: 13px; }
.form-card { max-width: 860px; }
</style>
