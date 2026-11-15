<template>
  <el-dialog
    :model-value="visible"
    @update:model-value="(v) => $emit('update:visible', v)"
    title="上传身份证影印本"
    width="min(800px, 94vw)"
    :show-close="!uploading"
    :close-on-click-modal="false"
    :close-on-press-escape="!uploading"
    destroy-on-close
    @closed="resetAll"
  >
    <div class="scanner-body" v-loading="uploading" :element-loading-text="loadingText">
      <el-alert
        v-if="!selectedFile"
        type="info"
        :closable="false"
        title="上传身份证正面"
        description="支持单张 JPG 或 PNG，最大 10MB。上传并识别会将图片发送给后端配置的 OCR 服务。"
      />
      <el-upload
        v-if="!selectedFile"
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
          <div class="el-upload__tip">百度 OCR 对编码后图片另有大小限制。请拍清证件四角及文字。</div>
        </template>
      </el-upload>

      <div v-else class="scanner-content" :class="{ 'scanner-content--review': recognized }">
        <div>
          <img :src="previewUrl" alt="身份证正面原图" class="scanner-preview__img" />
          <p class="scanner-preview__name">{{ selectedFile.name }}</p>
        </div>
        <div v-if="recognized" class="scanner-review">
          <h3>核对识别字段</h3>
          <p>对照原图逐项核对，确认后回填到建档表单。</p>
          <dl class="scanner-fields">
            <template v-for="field in reviewFields" :key="field.label">
              <dt>{{ field.label }}</dt>
              <dd>{{ field.value || '未识别' }}</dd>
            </template>
          </dl>
          <p>证件住址仅供核对。现居住地址由你填写。</p>
          <p v-if="result.ocrConfidence != null">识别置信度 {{ (result.ocrConfidence * 100).toFixed(1) }}%</p>
        </div>
      </div>

      <el-alert v-if="errorMessage" class="scanner-alert" type="warning" :closable="false" :title="errorMessage" show-icon />
    </div>

    <template #footer>
      <span class="dialog-footer">
        <el-button v-if="selectedFile" @click="resetAll" :disabled="uploading">重新选择</el-button>
        <el-button v-if="selectedFile" @click="useManual" :disabled="uploading">跳过 OCR，手工录入</el-button>
        <el-button v-if="recognized" type="primary" :disabled="uploading" @click="confirmResult">核对无误，回填表单</el-button>
        <el-button v-else-if="!result" type="primary" :loading="uploading" :disabled="!selectedFile" @click="upload(false)">上传并识别</el-button>
      </span>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, onBeforeUnmount, ref } from 'vue'
import { UploadFilled } from '@element-plus/icons-vue'
import { uploadIdCardImage } from '../../../api/persons'

defineProps({ visible: { type: Boolean, default: false } })
const emit = defineEmits(['update:visible', 'recognized'])

const selectedFile = ref(null)
const previewUrl = ref('')
const uploading = ref(false)
const loadingText = ref('')
const result = ref(null)
const errorMessage = ref('')
const recognized = computed(() => result.value?.ocrStatus === 'SUCCESS')
const reviewFields = computed(() => {
  const ocr = result.value
  if (!ocr) return []
  return [
    { label: '姓名', value: ocr.ocrName },
    { label: '身份证号', value: ocr.ocrIdcardFull },
    { label: '性别', value: { M: '男', F: '女' }[ocr.ocrGender] },
    { label: '出生日期', value: ocr.ocrBirthDate },
    { label: '民族', value: ocr.ocrEthnicity },
    { label: '证件住址', value: ocr.ocrAddress },
  ]
})

function onFileSelected(file) {
  const raw = file?.raw
  if (!raw || uploading.value) return
  resetAll()
  if (!['image/jpeg', 'image/png'].includes(raw.type) || raw.size === 0 || raw.size >= 10 * 1024 * 1024) {
    errorMessage.value = '请选择小于 10MB 的 JPG 或 PNG 图片'
    return
  }
  selectedFile.value = raw
  previewUrl.value = URL.createObjectURL(raw)
}

async function upload(skipOcr) {
  if (!selectedFile.value || uploading.value) return
  uploading.value = true
  loadingText.value = skipOcr ? '正在上传影印本…' : '正在上传并识别…'
  errorMessage.value = ''
  try {
    const body = new FormData()
    body.append('file', selectedFile.value, selectedFile.value.name)
    result.value = await uploadIdCardImage(body, { skipOcr, silent: true })
    if (skipOcr) {
      emit('recognized', result.value)
    } else if (result.value.ocrStatus === 'FAILED') {
      errorMessage.value = result.value.ocrError || '识别失败，可使用影印本继续手工录入'
    } else if (result.value.ocrStatus === 'SKIPPED') {
      errorMessage.value = '当前未配置 OCR，可使用影印本继续手工录入'
    }
  } catch (error) {
    errorMessage.value = error?.response?.data?.message || '身份证上传失败，请重试'
  } finally {
    uploading.value = false
  }
}

function useManual() {
  if (result.value?.ocrStatus === 'FAILED' || result.value?.ocrStatus === 'SKIPPED') {
    emit('recognized', result.value)
  } else {
    void upload(true)
  }
}

function confirmResult() {
  if (recognized.value && !uploading.value) emit('recognized', result.value)
}

function resetAll() {
  if (previewUrl.value) URL.revokeObjectURL(previewUrl.value)
  previewUrl.value = ''
  selectedFile.value = null
  result.value = null
  errorMessage.value = ''
}

onBeforeUnmount(resetAll)
</script>

<style scoped>
.scanner-body { min-height: 180px; }
.scanner-uploader { width: 100%; margin-top: 12px; }
.scanner-uploader__icon { font-size: 48px; color: var(--el-color-primary); }
.scanner-content { display: grid; gap: 16px; }
.scanner-content--review { grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); }
.scanner-preview__img { display: block; width: 100%; max-height: 360px; object-fit: contain; border-radius: 6px; border: 1px solid var(--el-border-color-lighter); }
.scanner-preview__name, .scanner-review p { color: var(--el-text-color-secondary); margin: 8px 0; overflow-wrap: anywhere; }
.scanner-review h3 { margin: 0; }
.scanner-fields { display: grid; grid-template-columns: max-content 1fr; gap: 8px 12px; }
.scanner-fields dt { color: var(--el-text-color-secondary); }
.scanner-fields dd { margin: 0; overflow-wrap: anywhere; }
.scanner-alert { margin-top: 12px; }
.dialog-footer { display: flex; flex-wrap: wrap; justify-content: flex-end; gap: 8px; }
.dialog-footer :deep(.el-button) { margin: 0; }
@media (max-width: 640px) {
  .scanner-content--review { grid-template-columns: 1fr; }
}
</style>
