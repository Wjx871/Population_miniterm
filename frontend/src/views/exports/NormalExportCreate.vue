<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <h1>普通脱敏导出</h1>
        <p class="subtitle">仅允许白名单模块/字段/过滤器；导出后通过鉴权接口下载，不暴露存储路径。</p>
      </div>
      <el-button @click="router.push('/exports')">返回记录</el-button>
    </div>

    <el-card shadow="never" class="form-card">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="120px">
        <el-form-item label="导出模块" prop="module">
          <el-select v-model="form.module" style="width: 100%" @change="onModuleChange">
            <el-option v-for="item in moduleOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>

        <el-form-item label="导出字段" prop="fields">
          <el-checkbox-group v-model="form.fields">
            <el-checkbox v-for="field in fieldOptions" :key="field.value" :label="field.value">
              {{ field.label }}
            </el-checkbox>
          </el-checkbox-group>
          <div class="form-tip">普通导出不可选择完整身份证、手机号、证件号或完整地址。</div>
        </el-form-item>

        <el-divider>过滤条件（可选）</el-divider>
        <el-form-item label="姓名">
          <el-input v-model.trim="form.filters.name" clearable />
        </el-form-item>
        <el-form-item label="行政区划">
          <el-input v-model.trim="form.filters.regionCode" clearable maxlength="20" />
        </el-form-item>
        <el-form-item label="状态">
          <el-input v-model.trim="form.filters.status" clearable />
        </el-form-item>
        <el-form-item label="创建时间">
          <el-date-picker
            v-model="createdRange"
            type="daterange"
            value-format="YYYY-MM-DD"
            start-placeholder="开始"
            end-placeholder="结束"
          />
        </el-form-item>

        <el-form-item>
          <el-button type="primary" :loading="submitting" @click="submitAndDownload">生成并下载</el-button>
          <el-button @click="router.back()">取消</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { createNormalExport } from '../../api/exports'
import { toNormalExportPayload } from '../../adapters/export'
import { getFieldOptions, getModuleOptions } from '../../constants/export'
import { downloadExportById } from '../../services/fileDownload'
import { getApiErrorMessage } from '../../utils/apiError'

const router = useRouter()
const formRef = ref()
const submitting = ref(false)
const createdRange = ref([])
const moduleOptions = getModuleOptions()

const form = reactive({
  module: 'PERSON',
  fields: ['name', 'maskedIdentityNo', 'gender', 'regionCode'],
  filters: {
    name: '',
    regionCode: '',
    status: '',
    createdFrom: '',
    createdTo: ''
  }
})

const fieldOptions = computed(() => getFieldOptions(form.module, { sensitive: false }))

const rules = {
  module: [{ required: true, message: '请选择导出模块', trigger: 'change' }],
  fields: [{ type: 'array', required: true, min: 1, message: '请至少选择一个字段', trigger: 'change' }]
}

watch(createdRange, (range) => {
  form.filters.createdFrom = range?.[0] || ''
  form.filters.createdTo = range?.[1] || ''
})

function onModuleChange() {
  form.fields = getFieldOptions(form.module, { sensitive: false })
    .filter((f) => !f.sensitive)
    .slice(0, 4)
    .map((f) => f.value)
}

async function submitAndDownload() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    const log = await createNormalExport(toNormalExportPayload(form))
    const exportLogId = log?.exportLogId || log?.id
    if (!exportLogId) {
      ElMessage.error('导出记录创建成功但未返回记录 ID')
      return
    }
    ElMessage.success('导出记录已生成，开始下载')
    await downloadExportById(exportLogId, log.fileName || 'export.xlsx')
    await router.push('/exports')
  } catch (error) {
    ElMessage.error(getApiErrorMessage(error, '普通导出失败'))
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.page-container { display: flex; flex-direction: column; gap: 16px; }
.page-header { display: flex; justify-content: space-between; align-items: end; }
.page-header h1 { margin: 0 0 8px; }
.subtitle, .form-tip { color: var(--el-text-color-secondary); font-size: 13px; }
.form-card { max-width: 860px; }
</style>
