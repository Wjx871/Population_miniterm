<template>
  <el-dialog
    :model-value="modelValue"
    title="关闭流动登记"
    width="480px"
    :close-on-click-modal="false"
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
      <el-form-item label="关闭原因" prop="reasonCode">
        <DictionarySelect v-model="form.reasonCode" type="FLOATING_CLOSE_REASON" placeholder="请选择关闭原因" style="width: 100%" />
      </el-form-item>
      <el-form-item label="关闭说明" prop="comment">
        <el-input v-model="form.comment" type="textarea" :rows="3" maxlength="500" show-word-limit placeholder="说明关闭原因和相关信息" />
      </el-form-item>
      <el-alert type="warning" :closable="false" show-icon style="margin-bottom: 8px">
        <template #title>
          关闭流动登记会结束当前登记。若该登记关联有效居住证，后端会同步注销证件并写入 REGISTRATION_CLOSED 日志。
        </template>
      </el-alert>
    </el-form>
    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">取消</el-button>
      <el-button type="danger" :loading="loading" @click="handleConfirm">确认关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { reactive, ref, watch } from 'vue'
import DictionarySelect from '../../../components/business/DictionarySelect.vue'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  loading: { type: Boolean, default: false },
  version: { type: Number, default: 0 }
})

const emit = defineEmits(['update:modelValue', 'confirm'])

const formRef = ref(null)
const form = reactive({ reasonCode: '', comment: '', version: 0 })

const rules = {
  reasonCode: [{ required: true, message: '请选择关闭原因', trigger: 'change' }],
  comment: [{ required: true, message: '请填写关闭说明', trigger: 'blur' }]
}

watch(() => props.modelValue, (val) => {
  if (val) {
    form.reasonCode = ''
    form.comment = ''
    form.version = props.version || 0
  }
})

function handleConfirm() {
  formRef.value?.validate((valid) => {
    if (!valid) return
    emit('confirm', {
      reasonCode: form.reasonCode,
      comment: form.comment,
      version: form.version
    })
  })
}
</script>
