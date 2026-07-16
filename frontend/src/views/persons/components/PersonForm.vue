<template>
  <el-form
    ref="formRef"
    :model="form"
    :rules="rules"
    label-width="100px"
  >
    <!-- Phase 14 / V4_013 身份证影印本必传 + OCR 识别 -->
    <el-form-item v-if="!isEdit" label="身份证影印本">
      <div class="idcard-block">
        <el-button :icon="Camera" type="primary" plain @click="openScanner">
          {{ image ? '重新扫描' : '扫描身份证' }}
        </el-button>
        <el-tag v-if="image" :type="imageTagType" effect="plain" class="idcard-tag">
          OCR：{{ ocrStatusText }}
        </el-tag>
        <el-button v-if="image" link type="danger" @click="clearImage">清除</el-button>
        <div v-if="image" class="idcard-meta">
          <span>影印本：{{ image.fileName }}（{{ formatBytes(image.fileSize) }}）</span>
          <span v-if="image.maskedIdCard">识别号：{{ image.maskedIdCard }}</span>
          <span v-if="image.confidence">置信度：{{ (image.confidence * 100).toFixed(1) }}%</span>
        </div>
        <div v-if="!image" class="idcard-hint">
          必须先上传身份证影印本。如需跳过 OCR，请使用「跳过 OCR」按钮。
        </div>
      </div>
    </el-form-item>

    <IdCardScannerDialog
      v-model:visible="scannerVisible"
      @recognized="onRecognized"
      @skipped="onSkipped"
    />

    <el-form-item label="姓名" prop="name">
      <el-input v-model="form.name" placeholder="请输入姓名" maxlength="50" show-word-limit />
    </el-form-item>
    <el-form-item label="性别" prop="gender">
      <el-radio-group v-model="form.gender">
        <el-radio value="男">男</el-radio>
        <el-radio value="女">女</el-radio>
      </el-radio-group>
    </el-form-item>
    <el-form-item label="身份证号" prop="idCard">
      <el-input
        v-model="form.idCard"
        placeholder="请输入身份证号"
        :disabled="isEdit"
        maxlength="18"
      />
    </el-form-item>
    <el-form-item label="出生日期" prop="birthDate">
      <el-date-picker
        v-model="form.birthDate"
        type="date"
        placeholder="请选择日期"
        value-format="YYYY-MM-DD"
        :disabled-date="disableFutureDate"
        style="width: 100%;"
      />
    </el-form-item>
    <el-form-item label="民族" prop="ethnicity">
      <DictionarySelect
        v-model="form.ethnicity"
        type="ETHNICITY"
        value-mode="label"
        placeholder="请选择民族"
        style="width: 100%;"
      />
    </el-form-item>
    <el-form-item label="联系电话" prop="phone">
      <el-input v-model="form.phone" placeholder="手机号（选填）" maxlength="20" />
    </el-form-item>
    <el-form-item label="现居住地址" prop="currentAddress">
      <el-input
        v-model="form.currentAddress"
        type="textarea"
        :rows="2"
        placeholder="请输入详细地址"
        maxlength="255"
        show-word-limit
      />
    </el-form-item>
    <!-- 基础表单不展示状态选择器；重大状态变更走后续业务流程 -->
  </el-form>
</template>

<script setup>
import { reactive, ref, watch, computed } from 'vue'
import { Camera } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { formatDate } from '../../../utils/date'
import { validateIdCard, validatePhone, validateBirthDate } from '../../../utils/validators'
import DictionarySelect from '../../../components/business/DictionarySelect.vue'
import IdCardScannerDialog from './IdCardScannerDialog.vue'

const props = defineProps({
  modelValue: {
    type: Object,
    default: () => ({}),
  },
  isEdit: {
    type: Boolean,
    default: false,
  },
})

const emit = defineEmits(['update:modelValue'])

const formRef = ref(null)
const form = reactive({
  name: '',
  gender: '男',
  idCard: '',
  birthDate: '',
  ethnicity: '汉族',
  phone: '',
  currentAddress: '',
  idCardImageId: null,
})

const image = ref(null)
const scannerVisible = ref(false)

const imageTagType = computed(() => {
  const status = image.value?.ocrStatus
  if (status === 'SUCCESS') return 'success'
  if (status === 'FAILED') return 'warning'
  if (status === 'SKIPPED') return 'info'
  return 'info'
})

const ocrStatusText = computed(() => {
  const status = image.value?.ocrStatus
  if (status === 'SUCCESS') return '识别成功'
  if (status === 'FAILED') return '识别失败'
  if (status === 'SKIPPED') return '已跳过'
  return '未知'
})

const rules = {
  name: [
    { required: true, message: '请输入姓名', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (!value || !String(value).trim()) {
          callback(new Error('请输入姓名'))
        } else {
          callback()
        }
      },
      trigger: 'blur',
    },
  ],
  gender: [{ required: true, message: '请选择性别', trigger: 'change' }],
  idCard: [
    { required: true, message: '请输入身份证号', trigger: 'blur' },
    { validator: validateIdCard, trigger: 'blur' },
  ],
  birthDate: [
    { required: true, message: '请选择出生日期', trigger: 'change' },
    { validator: validateBirthDate, trigger: 'change' },
  ],
  phone: [{ validator: validatePhone, trigger: 'blur' }],
}

function disableFutureDate(date) {
  const today = new Date()
  today.setHours(23, 59, 59, 999)
  return date.getTime() > today.getTime()
}

function syncFromModel(value) {
  const next = {
    name: value?.name ?? '',
    gender: value?.gender || '男',
    idCard: value?.idCard ?? '',
    birthDate: value?.birthDate ? formatDate(value.birthDate) : '',
    ethnicity: value?.ethnicity ?? '汉族',
    phone: value?.phone ?? '',
    currentAddress: value?.currentAddress ?? '',
    idCardImageId: value?.idCardImageId ?? null,
  }
  Object.assign(form, next)
  // 编辑模式不展示新增用的 image id，需要保留编辑前已存在的图；新增模式外层会注入新 imageId
  if (props.isEdit) {
    image.value = value?.idCardImage || null
  }
}

function openScanner() {
  scannerVisible.value = true
}

function onRecognized(result) {
  if (!result?.imageId) {
    ElMessage.warning('上传成功但未返回影印本标识')
    return
  }
  image.value = {
    imageId: result.imageId,
    fileName: result.originalFilename,
    fileSize: result.fileSize,
    ocrStatus: result.ocrStatus,
    maskedIdCard: result.ocrIdcardMasked,
    confidence: result.ocrConfidence,
  }
  form.idCardImageId = result.imageId
  applyOcrToForm(result)
  scannerVisible.value = false
}

function onSkipped(result) {
  if (result?.imageId) {
    onRecognized({ ...result, ocrStatus: 'SKIPPED', ocrIdcardMasked: null, ocrConfidence: null })
  } else {
    scannerVisible.value = false
  }
}

function applyOcrToForm(result) {
  if (result.ocrStatus !== 'SUCCESS') return
  // OCR 成功时回填身份证号、姓名、性别、出生日期、民族、地址，保留用户已手工修改的字段
  if (!form.idCard && result.ocrIdcardFull) form.idCard = result.ocrIdcardFull
  if (!form.name && result.ocrName) form.name = result.ocrName
  if (result.ocrGender === 'M') form.gender = '男'
  else if (result.ocrGender === 'F') form.gender = '女'
  if (!form.birthDate && result.ocrBirthDate) {
    form.birthDate = typeof result.ocrBirthDate === 'string'
      ? result.ocrBirthDate.substring(0, 10)
      : result.ocrBirthDate
  }
  if (!form.ethnicity && result.ocrEthnicity) form.ethnicity = result.ocrEthnicity
  if (!form.currentAddress && result.ocrAddress) form.currentAddress = result.ocrAddress
}

function clearImage() {
  image.value = null
  form.idCardImageId = null
}

function formatBytes(value) {
  if (!value) return '0 B'
  const units = ['B', 'KB', 'MB']
  let bytes = Number(value)
  let unit = 0
  while (bytes >= 1024 && unit < units.length - 1) {
    bytes /= 1024
    unit += 1
  }
  return `${bytes.toFixed(1)} ${units[unit]}`
}

watch(
  () => props.modelValue,
  (value) => {
    syncFromModel(value || {})
  },
  { immediate: true, deep: true }
)

watch(
  form,
  () => {
    emit('update:modelValue', { ...form })
  },
  { deep: true }
)

async function validate() {
  if (!formRef.value) return false
  try {
    await formRef.value.validate()
    return true
  } catch {
    return false
  }
}

function clearValidate() {
  formRef.value?.clearValidate()
}

function resetFields() {
  syncFromModel({})
  formRef.value?.clearValidate()
}

defineExpose({
  validate,
  clearValidate,
  resetFields,
  getForm: () => ({ ...form }),
})
</script>
