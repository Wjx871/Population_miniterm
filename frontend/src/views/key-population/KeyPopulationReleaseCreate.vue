<template>
  <div class="page-container" v-loading="loadingRecord">
    <div class="page-header">
      <div>
        <h1>重点人口解除申请</h1>
        <p class="subtitle">仅可对 ACTIVE 记录发起；创建后专业字段只读，提交走专业接口。</p>
      </div>
      <el-button @click="router.back()">返回</el-button>
    </div>

    <el-card v-if="record" shadow="never">
      <template #header>当前记录</template>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="姓名">{{ record.personName }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ record.status }}</el-descriptions-item>
        <el-descriptions-item label="重点类型">{{ record.populationType }}</el-descriptions-item>
        <el-descriptions-item label="关注等级">{{ record.attentionLevel }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <el-alert
      v-if="applicationId"
      type="success"
      :closable="false"
      show-icon
      title="解除草稿已创建。请前往申请详情上传材料并提交。"
    />

    <el-card shadow="never" class="form-card">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="120px">
        <el-form-item label="申请标题" prop="title">
          <el-input v-model.trim="form.title" maxlength="200" :disabled="Boolean(applicationId)" />
        </el-form-item>
        <el-form-item label="解除原因" prop="releaseReason">
          <el-input v-model.trim="form.releaseReason" type="textarea" :rows="3" :disabled="Boolean(applicationId)" />
        </el-form-item>
        <el-form-item label="解除日期" prop="releaseDate">
          <el-date-picker
            v-model="form.releaseDate"
            type="date"
            value-format="YYYY-MM-DD"
            :disabled="Boolean(applicationId)"
          />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model.trim="form.remark" type="textarea" :rows="2" :disabled="Boolean(applicationId)" />
        </el-form-item>
        <el-form-item>
          <el-button
            v-if="!applicationId"
            type="primary"
            :loading="saving"
            :disabled="record?.status !== 'ACTIVE'"
            @click="createDraft"
          >
            创建草稿
          </el-button>
          <el-button v-if="applicationId" type="primary" @click="goDetail">前往申请详情</el-button>
          <el-button @click="router.push(`/key-population/${recordId}`)">返回详情</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { createReleaseApplication, getKeyPopulationDetail } from '../../api/keyPopulation'
import { normalizeKeyPopulationRecord, toCreateReleasePayload } from '../../adapters/keyPopulation'
import { getApiErrorMessage } from '../../utils/apiError'

const route = useRoute()
const router = useRouter()
const formRef = ref()
const saving = ref(false)
const loadingRecord = ref(false)
const record = ref(null)
const applicationId = ref(null)
const recordId = computed(() => route.params.recordId)

const form = reactive({
  releaseReason: '',
  releaseDate: '',
  title: '',
  remark: ''
})

const rules = {
  title: [{ required: true, message: '请输入标题', trigger: 'blur' }],
  releaseReason: [{ required: true, message: '请填写解除原因', trigger: 'blur' }],
  releaseDate: [{ required: true, message: '请选择解除日期', trigger: 'change' }]
}

async function loadRecord() {
  loadingRecord.value = true
  try {
    record.value = normalizeKeyPopulationRecord(await getKeyPopulationDetail(recordId.value))
    if (record.value?.status !== 'ACTIVE') {
      ElMessage.warning('仅有效状态的记录可发起解除')
    }
  } catch (error) {
    ElMessage.error(getApiErrorMessage(error, '加载记录失败'))
  } finally {
    loadingRecord.value = false
  }
}

async function createDraft() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    const result = await createReleaseApplication(recordId.value, toCreateReleasePayload(form))
    applicationId.value = result?.application?.applicationId || result?.applicationId || result
    ElMessage.success('解除草稿已创建，正在进入申请详情')
    // 创建成功立即进入统一申请详情，避免刷新创建页重复建草稿
    await router.replace(`/applications/${applicationId.value}`)
  } catch (error) {
    ElMessage.error(getApiErrorMessage(error, '创建解除申请失败'))
  } finally {
    saving.value = false
  }
}

function goDetail() {
  if (applicationId.value) router.push(`/applications/${applicationId.value}`)
}

onMounted(loadRecord)
</script>

<style scoped>
.page-container { display: flex; flex-direction: column; gap: 16px; }
.page-header { display: flex; justify-content: space-between; align-items: end; }
.page-header h1 { margin: 0 0 8px; }
.subtitle { margin: 0; color: var(--el-text-color-secondary); }
.form-card { max-width: 860px; }
</style>
