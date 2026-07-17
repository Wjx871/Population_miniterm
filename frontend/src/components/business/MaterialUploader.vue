<template>
  <el-card shadow="never" class="material-uploader">
    <template #header>上传申请材料</template>
    <el-form :model="form" label-width="100px" @submit.prevent>
      <el-form-item label="材料类型" required>
        <el-select v-model="form.materialType" placeholder="请选择业务规则要求的材料" style="width: 100%" @change="applyMaterialRule">
          <el-option v-for="item in materialOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="材料名称" required>
        <el-input v-model="form.materialName" readonly />
      </el-form-item>
      <el-form-item label="业务规则">
        <el-tag type="primary">{{ form.conditionallyRequired ? '二选一，审批前必须核验' : form.requiredFlag ? '审批前必须核验' : '非必需材料' }}</el-tag>
        <span v-if="materialRuleText" class="rule-text">{{ materialRuleText }}</span>
      </el-form-item>
      <el-form-item label="选择文件" required>
        <el-upload :auto-upload="false" :limit="1" :accept="ACCEPTED_MATERIAL_TYPES" :on-change="onFileChange" :on-remove="onFileRemove">
          <el-button type="primary">选择 PDF/JPG/PNG 文件</el-button>
          <template #tip><div class="el-upload__tip">仅支持 PDF、JPG、JPEG、PNG，文件不超过 10MB。</div></template>
        </el-upload>
      </el-form-item>
      <el-button type="primary" :loading="uploading" :disabled="!canUpload" @click="submit">上传材料</el-button>
    </el-form>
  </el-card>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { uploadMaterial } from '../../api/materials'
import { ACCEPTED_MATERIAL_TYPES, MATERIAL_ACCEPTED_MIME_TYPES, MAX_MATERIAL_SIZE } from '../../constants/material'

const props = defineProps({
  applicationId: { type: [Number, String], required: true },
  materialOptions: { type: Array, required: true },
  materialRuleText: { type: String, default: '' },
})
const emit = defineEmits(['uploaded'])
const uploading = ref(false)
const file = ref(null)
const form = reactive({ materialType: '', materialName: '', requiredFlag: true, conditionallyRequired: false })
const canUpload = computed(() => Boolean(props.applicationId && form.materialType && form.materialName && file.value))

function onFileChange(uploadFile) {
  const raw = uploadFile.raw
  if (!raw) return
  if (raw.size > MAX_MATERIAL_SIZE) {
    ElMessage.error('材料文件不能超过 10MB')
    file.value = null
    return
  }
  const extension = raw.name?.split('.').pop()?.toLowerCase()
  const extensionAllowed = ['pdf', 'jpg', 'jpeg', 'png'].includes(extension)
  const mimeAllowed = !raw.type || MATERIAL_ACCEPTED_MIME_TYPES.includes(raw.type.toLowerCase())
  if (!extensionAllowed || !mimeAllowed) {
    ElMessage.error('仅支持 PDF、JPG、JPEG、PNG 文件')
    file.value = null
    return
  }
  file.value = raw
}

function onFileRemove() { file.value = null }

function applyMaterialRule(materialType) {
  const option = props.materialOptions.find((item) => item.value === materialType)
  form.materialName = option?.label || ''
  form.conditionallyRequired = Boolean(option?.conditionalRequired)
  form.requiredFlag = Boolean(option?.required || option?.conditionalRequired)
}

async function submit() {
  if (!canUpload.value) return
  uploading.value = true
  try {
    await uploadMaterial(props.applicationId, { ...form, file: file.value })
    ElMessage.success('材料上传成功')
    Object.assign(form, { materialType: '', materialName: '', requiredFlag: true, conditionallyRequired: false })
    file.value = null
    emit('uploaded')
  } finally {
    uploading.value = false
  }
}
</script>

<style scoped>
.rule-text { margin-left: 8px; color: var(--el-text-color-secondary); font-size: 13px; }
</style>
