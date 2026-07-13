<template>
  <el-form
    ref="formRef"
    :model="form"
    :rules="rules"
    label-width="100px"
  >
    <el-form-item v-if="isEdit" label="户籍编号">
      <el-input :model-value="householdNo" disabled />
    </el-form-item>

    <el-form-item v-else label="户籍编号" prop="householdNo">
      <el-input
        v-model="form.householdNo"
        placeholder="请输入户籍编号"
        maxlength="30"
        show-word-limit
        clearable
      />
    </el-form-item>

    <el-form-item v-if="isEdit" label="户主">
      <el-input :model-value="headPersonDisplay" disabled />
    </el-form-item>

    <el-form-item v-else label="户主" prop="headPersonId">
      <PersonSelect v-model="form.headPersonId" status="正常" />
    </el-form-item>

    <el-form-item label="户籍类型" prop="householdType">
      <DictionarySelect
        v-model="form.householdType"
        type="HOUSEHOLD_TYPE"
        placeholder="请选择户籍类型"
        :clearable="false"
      />
    </el-form-item>

    <el-form-item label="所属区划" prop="regionCode">
      <RegionCascader
        v-model="form.regionCode"
        :check-strictly="true"
        placeholder="请选择行政区划"
      />
    </el-form-item>

    <el-form-item label="户籍地址" prop="address">
      <el-input
        v-model="form.address"
        type="textarea"
        :rows="2"
        placeholder="请输入详细地址"
        maxlength="255"
        show-word-limit
      />
    </el-form-item>

    <el-form-item label="立户日期" prop="establishDate">
      <el-date-picker
        v-model="form.establishDate"
        type="date"
        placeholder="请选择日期"
        value-format="YYYY-MM-DD"
        :disabled-date="disableFutureDate"
        style="width: 100%;"
      />
    </el-form-item>

    <el-form-item v-if="isEdit && status" label="状态">
      <StatusTag :value="status" />
    </el-form-item>
  </el-form>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import PersonSelect from '../../../components/business/PersonSelect.vue'
import DictionarySelect from '../../../components/business/DictionarySelect.vue'
import RegionCascader from '../../../components/business/RegionCascader.vue'
import StatusTag from '../../../components/common/StatusTag.vue'
import { formatDate } from '../../../utils/date'

const props = defineProps({
  modelValue: {
    type: Object,
    default: () => ({}),
  },
  isEdit: {
    type: Boolean,
    default: false,
  },
  householdNo: {
    type: String,
    default: '',
  },
  headPersonName: {
    type: String,
    default: '',
  },
  headPersonId: {
    type: [Number, String],
    default: null,
  },
  status: {
    type: String,
    default: '',
  },
})

const emit = defineEmits(['update:modelValue'])

const formRef = ref(null)
const form = reactive({
  householdNo: '',
  headPersonId: null,
  householdType: 'FAMILY',
  regionCode: '',
  address: '',
  establishDate: '',
  status: '',
  version: 0,
})

const rules = computed(() => {
  const base = {
    householdType: [{ required: true, message: '请选择户籍类型', trigger: 'change' }],
    regionCode: [{ required: true, message: '请选择所属区划', trigger: 'change' }],
    address: [{ required: true, message: '请输入户籍地址', trigger: 'blur' }],
    establishDate: [{ required: true, message: '请选择立户日期', trigger: 'change' }],
  }
  if (!props.isEdit) {
    base.householdNo = [
      { required: true, message: '请输入户籍编号', trigger: 'blur' },
      {
        validator: (_rule, value, callback) => {
          const text = String(value || '').trim()
          if (!text) {
            callback(new Error('请输入户籍编号'))
            return
          }
          if (text.length > 30) {
            callback(new Error('户籍编号不能超过30个字符'))
            return
          }
          callback()
        },
        trigger: 'blur',
      },
    ]
    base.headPersonId = [{ required: true, message: '请选择户主', trigger: 'change' }]
  }
  return base
})

const headPersonDisplay = computed(() => {
  if (props.headPersonName) {
    return props.headPersonId
      ? `${props.headPersonName}（ID: ${props.headPersonId}）`
      : props.headPersonName
  }
  return props.headPersonId ? `ID: ${props.headPersonId}` : '-'
})

function disableFutureDate(date) {
  const today = new Date()
  today.setHours(23, 59, 59, 999)
  return date.getTime() > today.getTime()
}

function syncFromModel(value) {
  Object.assign(form, {
    householdNo: value?.householdNo ?? '',
    headPersonId: value?.headPersonId ?? null,
    householdType: value?.householdType || 'FAMILY',
    regionCode: value?.regionCode ?? '',
    address: value?.address ?? '',
    establishDate: value?.establishDate ? formatDate(value.establishDate) : '',
    status: value?.status ?? '',
    version: value?.version ?? 0,
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

defineExpose({
  validate,
  clearValidate,
  getForm: () => ({ ...form }),
})
</script>
