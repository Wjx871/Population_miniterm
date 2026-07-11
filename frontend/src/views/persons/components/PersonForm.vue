<template>
  <el-form
    ref="formRef"
    :model="form"
    :rules="rules"
    label-width="100px"
  >
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
      <el-input v-model="form.ethnicity" placeholder="如：汉族" maxlength="30" />
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
import { reactive, ref, watch } from 'vue'
import { formatDate } from '../../../utils/date'
import { validateIdCard, validatePhone, validateBirthDate } from '../../../utils/validators'

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
  Object.assign(form, {
    name: value?.name ?? '',
    gender: value?.gender || '男',
    idCard: value?.idCard ?? '',
    birthDate: value?.birthDate ? formatDate(value.birthDate) : '',
    ethnicity: value?.ethnicity ?? '汉族',
    phone: value?.phone ?? '',
    currentAddress: value?.currentAddress ?? '',
  })
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
