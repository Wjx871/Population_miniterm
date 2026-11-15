<template>
  <el-card shadow="never">
    <template #header>材料一致性预核对</template>
    <p class="check-note">选择同一人的身份证正面、户口簿成员页和迁入申请表。开始后，所选图片会发送给百度智能云 OCR。</p>
    <el-form label-position="top" @submit.prevent>
      <div class="check-options">
        <el-form-item v-for="type in materialTypes" :key="type.key" :label="type.label">
          <el-select v-model="selection[type.key]" :disabled="loading" clearable :aria-label="type.label"
            :placeholder="options[type.key].length ? '选择已上传的图片' : '暂无对应图片'">
            <el-option v-for="item in options[type.key]" :key="item.materialId" :value="item.materialId"
              :label="item.originalFilename || item.materialName" />
          </el-select>
        </el-form-item>
      </div>
      <el-button type="primary" plain :loading="loading" :disabled="selectedCount < 2" @click="runCheck">开始预核对</el-button>
      <span v-if="selectedCount < 2" class="check-hint">至少选择两份 JPG 或 PNG 图片。</span>
    </el-form>

    <el-alert v-if="error" class="check-result" :title="error" type="warning" :closable="false" />
    <div v-if="result" class="check-result">
      <el-alert :title="summaryLabels[result.status]" :type="result.status === 'MATCH' ? 'success' : 'warning'"
        :closable="false" show-icon />
      <p class="check-note">已选材料 · 姓名 {{ statusLabels[result.nameStatus] }} · 证件号 {{ statusLabels[result.idCardStatus] }}</p>
      <el-table :data="result.documents" border>
        <el-table-column label="材料" min-width="190">
          <template #default="{ row }">
            <div>{{ typeLabels[row.materialType] }}</div>
            <small>{{ row.materialName || '未选择' }}</small>
          </template>
        </el-table-column>
        <el-table-column label="识别姓名" min-width="150">
          <template #default="{ row }">{{ row.name.value || (row.materialId ? row.name.issue : '—') }}</template>
        </el-table-column>
        <el-table-column label="识别证件号" min-width="180">
          <template #default="{ row }">{{ row.idCard.value || (row.materialId ? row.idCard.issue : '—') }}</template>
        </el-table-column>
        <el-table-column label="原图" width="116">
          <template #default="{ row }">
            <el-button v-if="row.materialId" link type="primary" @click="showOriginal(row)">查看原图</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="previewOpen" title="材料原图" width="min(760px, 94vw)" append-to-body @close="resetOriginal">
      <div v-loading="previewLoading">
        <el-alert v-if="previewError" :title="previewError" type="warning" :closable="false" />
        <div v-if="previewUrl" class="original-frame">
          <img :src="previewUrl" :alt="`${previewLabel}原图`" @load="readImageSize" />
          <span v-for="box in highlights" :key="box.label" class="original-highlight" :style="box.style"
            :title="box.label" />
        </div>
      </div>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { checkMigrationMaterials, downloadMaterial } from '../../../api/materials'
import { getApiErrorMessage } from '../../../utils/apiError'

const props = defineProps({
  applicationId: { type: [Number, String], required: true },
  materials: { type: Array, required: true },
})

const materialTypes = [
  { key: 'identityMaterialId', type: 'IDENTITY_PROOF', label: '身份证正面' },
  { key: 'householdMaterialId', type: 'HOUSEHOLD_BOOK', label: '户口簿成员页' },
  { key: 'formMaterialId', type: 'APPLICATION_FORM', label: '迁入申请表（选传）' },
]
const typeLabels = Object.fromEntries(materialTypes.map(item => [item.type, item.label]))
const statusLabels = { MATCH: '一致', CONFLICT: '疑似冲突', MISSING: '材料未齐', UNCERTAIN: '识别不足' }
const summaryLabels = {
  MATCH: '三份材料的姓名和证件号一致',
  CONFLICT: '发现疑似字段冲突，请对照原图核实',
  MISSING: '三类核对材料未选齐，请查看已选材料的结果',
  UNCERTAIN: '部分字段识别不足，请对照原图人工核对',
}
const selection = reactive({ identityMaterialId: null, householdMaterialId: null, formMaterialId: null })
const options = computed(() => Object.fromEntries(materialTypes.map(item => [
  item.key,
  props.materials.filter(material => material.materialType === item.type && ['image/jpeg', 'image/png'].includes(material.contentType)),
])))
const selectedCount = computed(() => Object.values(selection).filter(Boolean).length)
const loading = ref(false)
const result = ref(null)
const error = ref('')
let activeCheck

async function runCheck() {
  if (loading.value || selectedCount.value < 2) return
  clearCheck()
  const request = new AbortController()
  activeCheck = request
  loading.value = true
  try {
    const response = await checkMigrationMaterials(props.applicationId, { ...selection }, request.signal)
    if (activeCheck === request) result.value = response
  } catch (failure) {
    if (activeCheck === request) error.value = getApiErrorMessage(failure, '材料预核对失败，请稍后再试。')
  } finally {
    if (activeCheck === request) {
      activeCheck = undefined
      loading.value = false
    }
  }
}

function clearCheck() {
  activeCheck?.abort()
  activeCheck = undefined
  loading.value = false
  result.value = null
  error.value = ''
  previewOpen.value = false
  resetOriginal()
}

const previewOpen = ref(false)
const previewLoading = ref(false)
const previewError = ref('')
const previewUrl = ref('')
const previewDocument = ref(null)
const imageSize = ref(null)
const previewLabel = computed(() => typeLabels[previewDocument.value?.materialType])
const highlights = computed(() => {
  const size = imageSize.value
  const document = previewDocument.value
  if (!size || !document) return []
  return [{ label: '姓名', box: document.name.location }, { label: '证件号', box: document.idCard.location }]
    .filter(({ box }) => box && box.left + box.width <= size.width && box.top + box.height <= size.height)
    .map(({ label, box }) => ({
      label,
      style: {
        left: `${100 * box.left / size.width}%`, top: `${100 * box.top / size.height}%`,
        width: `${100 * box.width / size.width}%`, height: `${100 * box.height / size.height}%`,
      },
    }))
})
let previewRequest = 0

function readImageSize(event) {
  const { naturalWidth: width, naturalHeight: height } = event.target
  imageSize.value = width && height ? { width, height } : null
}

async function showOriginal(document) {
  resetOriginal()
  const request = previewRequest
  previewDocument.value = document
  previewOpen.value = true
  previewLoading.value = true
  try {
    const { data } = await downloadMaterial(document.materialId)
    if (request !== previewRequest) return
    if (!['image/jpeg', 'image/png'].includes(data.type)) {
      previewError.value = '材料格式无法预览'
      return
    }
    previewUrl.value = URL.createObjectURL(data)
  } catch (failure) {
    if (request === previewRequest) previewError.value = getApiErrorMessage(failure, '原图读取失败')
  } finally {
    if (request === previewRequest) previewLoading.value = false
  }
}

function resetOriginal() {
  previewRequest++
  if (previewUrl.value) URL.revokeObjectURL(previewUrl.value)
  previewUrl.value = ''
  previewLoading.value = false
  previewError.value = ''
  previewDocument.value = null
  imageSize.value = null
}

watch(() => ({ ...selection }), clearCheck)
watch(() => props.applicationId, () => {
  for (const item of materialTypes) selection[item.key] = null
  clearCheck()
})
watch(options, next => {
  for (const item of materialTypes) {
    if (selection[item.key] && !next[item.key].some(option => option.materialId === selection[item.key])) {
      selection[item.key] = null
    }
  }
})
onBeforeUnmount(clearCheck)
</script>

<style scoped>
.check-options { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 12px; }
.check-options .el-select { width: 100%; }
.check-note, .check-hint, small { color: var(--el-text-color-secondary); }
.check-note { margin: 0 0 12px; }
.check-hint { margin-left: 10px; }
.check-result { margin-top: 16px; }
.check-result .check-note { margin: 12px 0; }
.original-frame { position: relative; margin: 0 auto; }
.original-frame img { display: block; width: 100%; height: auto; }
.original-highlight { position: absolute; outline: 2px solid var(--el-color-primary); background: var(--el-color-primary-light-8); opacity: 0.55; }
@media (max-width: 720px) { .check-options { grid-template-columns: 1fr; gap: 0; } }
</style>
