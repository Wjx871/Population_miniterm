<template>
  <el-dialog
    :model-value="modelValue"
    :title="executeTitle"
    width="480px"
    :close-on-click-modal="false"
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
      <el-form-item v-if="executeType === 'PERMIT_ISSUE'" label="签发机关" prop="issuingAuthority">
        <el-input v-model="form.issuingAuthority" maxlength="100" show-word-limit placeholder="请输入签发机关名称" />
      </el-form-item>
      <el-form-item label="版本号" prop="version">
        <span class="version-text">{{ form.version }}</span>
      </el-form-item>
      <el-alert v-if="executeType === 'PERMIT_ISSUE'" type="warning" :closable="false" show-icon style="margin-bottom: 8px">
        <template #title>课程模拟系统生成，不作为真实政务证件</template>
      </el-alert>
      <el-alert type="info" :closable="false" show-icon>
        <template #title>执行后将由后端正式生成业务记录。请确认信息无误。</template>
      </el-alert>
    </el-form>
    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="loading" @click="handleConfirm">{{ executeTitle }}</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { EXECUTE_TYPE } from '../../../constants/floatingResidence'
import { isValidVersion } from '../../../utils/professionalApplication'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  executeType: { type: String, required: true },
  version: { type: Number, default: null },
  loading: { type: Boolean, default: false }
})

const emit = defineEmits(['update:modelValue', 'confirm'])

const formRef = ref(null)
const form = reactive({ issuingAuthority: '', version: null })

const executeTitle = computed(() => {
  const titles = {
    [EXECUTE_TYPE.FLOATING_EXECUTE]: '执行流动登记',
    [EXECUTE_TYPE.PERMIT_ISSUE]: '签发居住证',
    [EXECUTE_TYPE.PERMIT_ENDORSE]: '执行签注',
    [EXECUTE_TYPE.PERMIT_CANCEL]: '执行注销'
  }
  return titles[props.executeType] || '执行操作'
})

const rules = computed(() => {
  const base = {
    version: [{
      validator: (_, value, callback) => {
        if (isValidVersion(value)) callback()
        else callback(new Error('版本号无效'))
      },
      trigger: 'change'
    }]
  }
  if (props.executeType === 'PERMIT_ISSUE') {
    return {
      ...base,
      issuingAuthority: [{ required: true, message: '请输入签发机关', trigger: 'blur' }]
    }
  }
  return base
})

watch(() => props.modelValue, (val) => {
  if (val) {
    form.issuingAuthority = ''
    form.version = props.version
  }
})

function handleConfirm() {
  formRef.value?.validate((valid) => {
    if (!valid) return
    const result = { version: form.version }
    if (props.executeType === 'PERMIT_ISSUE') {
      result.issuingAuthority = form.issuingAuthority
    }
    emit('confirm', result)
  })
}
</script>

<style scoped>
.version-text{font-family:monospace;color:var(--el-text-color-secondary)}
</style>
