<template>
  <el-button v-if="isImage" plain @click="open">{{ title }}</el-button>
  <span v-else class="review-note">识别支持单页 JPG 或 PNG，PDF 可直接上传材料。</span>

  <el-dialog v-model="visible" :title="title" width="min(860px, 94vw)"
    append-to-body :close-on-click-modal="false" @closed="reset">
    <p class="review-note">{{ isHousehold ? '请选择图片的实际页型。' : '请使用文字清晰、方向正确的单页申请表。' }}点击识别后，此页会发送给百度智能云 OCR。</p>
    <el-radio-group v-if="isHousehold" v-model="page" :disabled="loading" @change="clearResult">
      <el-radio-button value="homepage">户主页</el-radio-button>
      <el-radio-button value="subpage">成员页</el-radio-button>
    </el-radio-group>

    <div class="review-content">
      <div v-if="previewUrl" class="review-image-frame">
        <img :src="previewUrl" :alt="`${materialLabel}原图`" class="review-image" @load="readImageSize" />
        <span v-if="highlight" class="review-highlight" :style="highlight" aria-hidden="true" />
      </div>
      <div v-if="result">
        <p class="review-note">对照本次第 {{ result.pageNumber }} 页原图逐项核对。</p>
        <dl class="review-fields">
          <template v-for="field in result.fields" :key="field.key">
            <dt>{{ field.label }}</dt>
            <dd>
              <button v-if="field.location" type="button" class="review-field-location"
                :aria-pressed="selectedField === field" @click="selectedField = field">{{ field.value }}</button>
              <span v-else>{{ field.value }}</span>
            </dd>
          </template>
        </dl>
        <el-alert v-for="warning in result.warnings" :key="warning" :title="warning" type="warning" :closable="false" show-icon />
      </div>
    </div>
    <el-alert v-if="error" :title="error" type="warning" :closable="false" show-icon />

    <template #footer>
      <el-button @click="visible = false">返回材料上传</el-button>
      <el-button type="primary" :loading="loading" :disabled="loading || Boolean(result)" @click="recognize">
        发送并识别
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { previewMigrationMaterial } from '../../api/materials'
import { getApiErrorMessage } from '../../utils/apiError'

const props = defineProps({
  applicationId: { type: [Number, String], required: true },
  file: { type: File, required: true },
  materialType: { type: String, required: true },
})

const visible = ref(false)
const page = ref('homepage')
const previewUrl = ref('')
const result = ref(null)
const error = ref('')
const loading = ref(false)
const imageSize = ref(null)
const selectedField = ref(null)
const isHousehold = computed(() => props.materialType === 'HOUSEHOLD_BOOK')
const materialLabel = computed(() => isHousehold.value ? '户口簿' : '迁入申请表')
const title = computed(() => `${materialLabel.value}识别预览`)
const isImage = computed(() => ['image/jpeg', 'image/png'].includes(props.file.type))
const highlight = computed(() => {
  const box = selectedField.value?.location
  const size = imageSize.value
  if (!box || !size || box.left + box.width > size.width || box.top + box.height > size.height) return null
  return {
    left: `${box.left / size.width * 100}%`,
    top: `${box.top / size.height * 100}%`,
    width: `${box.width / size.width * 100}%`,
    height: `${box.height / size.height * 100}%`,
  }
})
let activeRequest

function readImageSize(event) {
  const { naturalWidth: width, naturalHeight: height } = event.target
  imageSize.value = width && height ? { width, height } : null
}

function clearResult() {
  result.value = null
  selectedField.value = null
  error.value = ''
}

function reset() {
  activeRequest?.abort()
  activeRequest = undefined
  loading.value = false
  if (previewUrl.value) URL.revokeObjectURL(previewUrl.value)
  previewUrl.value = ''
  imageSize.value = null
  clearResult()
}

function open() {
  reset()
  page.value = 'homepage'
  previewUrl.value = URL.createObjectURL(props.file)
  visible.value = true
}

async function recognize() {
  if (loading.value || !isImage.value) return
  clearResult()
  if (props.file.size > 3_000_000) {
    error.value = '图片超过识别大小限制，请缩小图片后重试。'
    return
  }
  const request = new AbortController()
  activeRequest = request
  loading.value = true
  try {
    const recognized = await previewMigrationMaterial(
      props.applicationId, props.file, isHousehold.value ? page.value : null, request.signal,
    )
    if (activeRequest === request) result.value = recognized
  } catch (failure) {
    if (activeRequest === request) error.value = getApiErrorMessage(failure, '识别失败，可直接上传材料并人工核对。')
  } finally {
    if (activeRequest === request) {
      loading.value = false
      activeRequest = undefined
    }
  }
}

watch([() => props.file, () => props.applicationId, () => props.materialType], () => {
  visible.value = false
  reset()
})
onBeforeUnmount(reset)
</script>

<style scoped>
.review-note { margin: 0 0 10px; color: var(--el-text-color-secondary); }
.review-content { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); gap: 16px; margin: 14px 0; }
.review-image-frame { position: relative; align-self: start; outline: 1px solid var(--el-border-color-lighter); border-radius: 6px; overflow: hidden; }
.review-image { display: block; width: 100%; height: auto; }
.review-highlight { position: absolute; outline: 2px solid var(--el-color-primary); background: var(--el-color-primary-light-8); opacity: 0.65; pointer-events: none; }
.review-field-location { padding: 0; background: none; border: 0; color: var(--el-color-primary); font: inherit; text-align: left; text-decoration: underline; cursor: pointer; overflow-wrap: anywhere; }
.review-fields { display: grid; grid-template-columns: max-content 1fr; gap: 8px 10px; margin: 0 0 10px; }
.review-fields dt { color: var(--el-text-color-secondary); }
.review-fields dd { margin: 0; overflow-wrap: anywhere; }
@media (max-width: 640px) { .review-content { grid-template-columns: 1fr; } }
</style>
