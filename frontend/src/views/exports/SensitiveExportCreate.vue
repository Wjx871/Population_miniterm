<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <h1>敏感导出申请</h1>
        <p class="subtitle">创建草稿后可按需上传补充材料，再提交审批；审批通过后由授权经办人显式执行生成文件。</p>
      </div>
      <el-button @click="router.push('/exports')">返回记录</el-button>
    </div>

    <el-alert
      v-if="applicationId"
      type="info"
      :closable="false"
      show-icon
      title="敏感导出专业字段创建后不可再修改，请通过申请详情上传材料并提交。"
      style="margin-bottom: 8px"
    />

    <el-card shadow="never" class="form-card">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="120px">
        <el-form-item label="申请标题" prop="title">
          <el-input v-model.trim="form.title" maxlength="200" show-word-limit :disabled="Boolean(applicationId)" />
        </el-form-item>
        <el-form-item label="导出模块" prop="module">
          <el-select v-model="form.module" style="width: 100%" :disabled="Boolean(applicationId)" @change="onModuleChange">
            <el-option v-for="item in moduleOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="导出字段" prop="fields">
          <el-checkbox-group v-model="form.fields" :disabled="Boolean(applicationId)">
            <el-checkbox v-for="field in fieldOptions" :key="field.value" :label="field.value">
              {{ field.label }}{{ field.sensitive ? '（敏感）' : '' }}
            </el-checkbox>
          </el-checkbox-group>
        </el-form-item>
        <el-form-item label="预计上限" prop="expectedRowLimit">
          <el-input-number v-model="form.expectedRowLimit" :min="1" :disabled="Boolean(applicationId)" />
          <div class="form-tip">最终额度由后端策略校验，前端不预设业务上限。</div>
        </el-form-item>
        <el-form-item label="导出理由" prop="reason">
          <el-input v-model.trim="form.reason" type="textarea" :rows="3" :disabled="Boolean(applicationId)" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model.trim="form.remark" type="textarea" :rows="2" maxlength="500" :disabled="Boolean(applicationId)" />
        </el-form-item>

        <el-divider>过滤条件（可选）</el-divider>
        <el-form-item label="姓名">
          <el-input v-model.trim="form.filters.name" clearable :disabled="Boolean(applicationId)" />
        </el-form-item>
        <el-form-item label="行政区划">
          <el-input v-model.trim="form.filters.regionCode" clearable :disabled="Boolean(applicationId)" />
        </el-form-item>
        <el-form-item label="状态">
          <el-input v-model.trim="form.filters.status" clearable :disabled="Boolean(applicationId)" />
        </el-form-item>

        <el-form-item>
          <el-button v-if="!applicationId" type="primary" :loading="saving" @click="createDraft">创建草稿</el-button>
          <el-button v-if="applicationId" type="primary" @click="goApplication">前往申请详情</el-button>
          <el-button @click="router.back()">返回</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { createSensitiveExportApplication } from '../../api/exports'
import { toSensitiveExportPayload } from '../../adapters/export'
import { getFieldOptions, getModuleOptions } from '../../constants/export'
import { getApiErrorMessage } from '../../utils/apiError'

const router = useRouter()
const formRef = ref()
const saving = ref(false)
const applicationId = ref(null)
const moduleOptions = getModuleOptions()

const form = reactive({
  module: 'PERSON',
  fields: ['name', 'identityNo', 'regionCode'],
  filters: { name: '', regionCode: '', status: '', createdFrom: '', createdTo: '' },
  reason: '',
  expectedRowLimit: 1000,
  title: '',
  remark: ''
})

const fieldOptions = computed(() => getFieldOptions(form.module, { sensitive: true }))

const rules = {
  title: [{ required: true, message: '请输入申请标题', trigger: 'blur' }],
  module: [{ required: true, message: '请选择模块', trigger: 'change' }],
  fields: [{ type: 'array', required: true, min: 1, message: '请选择字段', trigger: 'change' }],
  expectedRowLimit: [{ required: true, message: '请填写预计上限', trigger: 'change' }],
  reason: [{ required: true, message: '请填写导出理由', trigger: 'blur' }]
}

function onModuleChange() {
  form.fields = getFieldOptions(form.module, { sensitive: true }).slice(0, 3).map((f) => f.value)
}

async function createDraft() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    const id = await createSensitiveExportApplication(toSensitiveExportPayload(form))
    applicationId.value = id?.applicationId || id
    ElMessage.success('敏感导出草稿已创建，正在进入申请详情')
    // 创建成功立即进入统一申请详情，避免刷新创建页重复建草稿
    await router.replace(`/applications/${applicationId.value}`)
  } catch (error) {
    ElMessage.error(getApiErrorMessage(error, '创建敏感导出申请失败'))
  } finally {
    saving.value = false
  }
}

function goApplication() {
  if (applicationId.value) router.push(`/applications/${applicationId.value}`)
}
</script>

<style scoped>
.page-container { display: flex; flex-direction: column; gap: 16px; }
.page-header { display: flex; justify-content: space-between; align-items: end; }
.page-header h1 { margin: 0 0 8px; }
.subtitle { margin: 0; color: var(--el-text-color-secondary); }
.form-card { max-width: 900px; }
.form-tip { color: var(--el-text-color-secondary); font-size: 12px; margin-top: 4px; }
</style>
