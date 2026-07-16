<template>
  <el-dialog
    :model-value="visible"
    @update:model-value="(v) => $emit('update:visible', v)"
    title="上传身份证影印本"
    width="640px"
    destroy-on-close
    @closed="resetAll"
  >
    <div class="scanner-body" v-loading="uploading" :element-loading-text="loadingText">
      <el-alert
        v-if="!imagePreviewUrl"
        type="info"
        :closable="false"
        title="使用说明"
        description="支持 JPG / PNG / PDF 文件，建议分辨率 ≥ 1024×720。OCR 模块将尝试识别身份证号、姓名、性别、出生日期、地址与民族（仅供录入参考）。"
      />
      <el-upload
        v-if="!imagePreviewUrl"
        class="scanner-uploader"
        drag
        :auto-upload="false"
        accept="image/jpeg,image/png"
        :on-change="onFileSelected"
        :show-file-list="false"
      >
        <el-icon class="scanner-uploader__icon"><UploadFilled /></el-icon>
        <div class="el-upload__text">点击或拖拽身份证正面照至此处</div>
        <template #tip>
          <div class="el-upload__tip">单张 JPG / PNG，最大 10MB。识别失败可继续手工录入。</div>
        </template>
      </el-upload>

      <div v-else class="scanner-preview">
        <img v-if="isImagePreview" :src="imagePreviewUrl" alt="身份证影印本预览" class="scanner-preview__img" />
        <el-icon v-else class="scanner-preview__icon"><Document /></el-icon>
        <div class="scanner-preview__meta">
          <div>文件名：{{ fileMeta?.name || '已上传文件' }}</div>
          <div>大小：{{ formatBytes(fileMeta?.size || 0) }}</div>
          <div>
            OCR 状态：
            <el-tag :type="statusTagType" size="small">{{ statusText }}</el-tag>
          </div>
          <div v-if="ocrResult?.confidence" class="confidence">识别置信度：{{ (ocrResult.confidence * 100).toFixed(1) }}%</div>
        </div>
        <div v-if="errorMessage" class="scanner-preview__error">
          <el-alert type="warning" :closable="false" :title="errorMessage" />
        </div>
      </div>
    </div>

    <template #footer>
      <span class="dialog-footer">
        <el-button @click="handleSkip" :disabled="uploading">跳过 OCR</el-button>
        <el-button @click="handleReupload" :disabled="uploading">重新上传</el-button>
        <el-button type="primary" :loading="uploading" :disabled="!fileMeta" @click="handleUpload">上传并识别</el-button>
      </span>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { UploadFilled, Document } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { uploadIdCardImage } from '../../../api/persons'

const props = defineProps({
  visible: { type: Boolean, default: false },
})
const emit = defineEmits(['update:visible', 'recognized', 'skipped'])

const fileMeta = ref(null)
const imagePreviewUrl = ref('')
const uploading = ref(false)
const ocrResult = ref(null)
const errorMessage = ref('')

const isImagePreview = computed(() => {
  const type = fileMeta.value?.type || ''
  return type.startsWith('image/')
})

const loadingText = computed(() => (fileMeta.value ? '正在上传并执行 OCR...' : ''))

const statusText = computed(() => {
  const status = ocrResult.value?.ocrStatus
  if (status === 'SUCCESS') return '识别成功'
  if (status === 'FAILED') return '识别失败（可手工录入）'
  if (status === 'SKIPPED') return '已跳过识别'
  return '未识别'
})

const statusTagType = computed(() => {
  const status = ocrResult.value?.ocrStatus
  if (status === 'SUCCESS') return 'success'
  if (status === 'FAILED') return 'warning'
  if (status === 'SKIPPED') return 'info'
  return 'info'
})

watch(
  () => props.visible,
  (val) => {
    if (!val) resetAll()
  },
)

function onFileSelected(file) {
  if (!file?.raw) return
  fileMeta.value = { name: file.name, size: file.size, type: file.raw.type || 'application/octet-stream', raw: file.raw }
  if (file.raw.type?.startsWith('image/')) {
    if (imagePreviewUrl.value) URL.revokeObjectURL(imagePreviewUrl.value)
    imagePreviewUrl.value = URL.createObjectURL(file.raw)
  } else {
    imagePreviewUrl.value = ''
  }
  errorMessage.value = ''
  ocrResult.value = null
}

async function handleUpload() {
  if (!fileMeta.value?.raw) {
    ElMessage.warning('请先选择身份证图片')
    return
  }
  uploading.value = true
  errorMessage.value = ''
  try {
    const formData = new FormData()
    formData.append('file', fileMeta.value.raw, fileMeta.value.name)
    const result = await uploadIdCardImage(formData, { skipOcr: false })
    ocrResult.value = result
    if (result.ocrStatus === 'FAILED') {
      errorMessage.value = result.ocrError || 'OCR 识别失败，请检查图片清晰度'
    } else if (result.ocrStatus === 'SUCCESS' && result.ocrIdcardMasked) {
      ElMessage.success(`已识别（${result.ocrIdcardMasked}），请核对后保存`)
    }
    emit('recognized', result)
  } catch (error) {
    const message = error?.response?.data?.message || error?.message || '身份证上传失败'
    errorMessage.value = message
  } finally {
    uploading.value = false
  }
}

async function handleSkip() {
  if (!fileMeta.value?.raw) {
    emit('skipped', null)
    emit('update:visible', false)
    return
  }
  uploading.value = true
  errorMessage.value = ''
  try {
    const formData = new FormData()
    formData.append('file', fileMeta.value.raw, fileMeta.value.name)
    const result = await uploadIdCardImage(formData, { skipOcr: true })
    ocrResult.value = result
    emit('recognized', result)
  } catch (error) {
    const message = error?.response?.data?.message || error?.message || '身份证上传失败'
    errorMessage.value = message
  } finally {
    uploading.value = false
  }
}

function handleReupload() {
  fileMeta.value = null
  if (imagePreviewUrl.value) {
    URL.revokeObjectURL(imagePreviewUrl.value)
    imagePreviewUrl.value = ''
  }
  ocrResult.value = null
  errorMessage.value = ''
}

function resetAll() {
  handleReupload()
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

defineExpose({
  fillOcrResult,
})

function fillOcrResult(target = {}) {
  if (!ocrResult.value) return target
  const ocr = ocrResult.value
  if (ocr.ocrName) target.name = ocr.ocrName
  if (ocr.ocrGender === 'M') target.gender = '男'
  else if (ocr.ocrGender === 'F') target.gender = '女'
  if (ocr.ocrBirthDate) target.birthDate = ocr.ocrBirthDate
  if (ocr.ocrEthnicity) target.ethnicity = ocr.ocrEthnicity
  if (ocr.ocrAddress) target.currentAddress = ocr.ocrAddress
  return target
}
</script>

<style scoped>
.scanner-body { min-height: 200px; padding: 4px 0; }
.scanner-uploader { width: 100%; }
.scanner-uploader__icon { font-size: 48px; color: var(--el-color-primary); }
.scanner-preview { display: flex; flex-direction: column; gap: 12px; }
.scanner-preview__img { max-width: 100%; max-height: 320px; object-fit: contain; border-radius: 4px; border: 1px solid var(--el-border-color-lighter); }
.scanner-preview__icon { font-size: 64px; color: var(--el-color-info); text-align: center; }
.scanner-preview__meta { display: flex; flex-direction: column; gap: 6px; font-size: 13px; color: var(--el-text-color-regular); }
.scanner-preview__error { margin-top: 8px; }
.confidence { color: var(--el-color-info); }
.dialog-footer { display: flex; gap: 8px; justify-content: flex-end; }
</style>
