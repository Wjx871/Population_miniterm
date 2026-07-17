<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <h1>重点人口建档申请</h1>
        <p class="subtitle">创建草稿后专业字段只读；上传材料后提交须走专业 submit 接口。</p>
      </div>
      <el-button @click="goBackOrFallback(router, '/key-population')">返回</el-button>
    </div>

    <el-alert
      v-if="applicationId"
      type="success"
      :closable="false"
      show-icon
      title="草稿已创建。专业字段不可再修改，请上传材料后前往申请详情提交。"
    />

    <el-card shadow="never" class="form-card">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="120px">
        <el-form-item label="申请标题" prop="title">
          <el-input v-model.trim="form.title" maxlength="200" show-word-limit :disabled="Boolean(applicationId)" />
        </el-form-item>
        <el-form-item label="办理人员" prop="personId">
          <PersonSelect v-model="form.personId" :disabled="Boolean(applicationId)" />
        </el-form-item>
        <el-form-item label="重点类型" prop="populationType">
          <DictionarySelect
            v-model="form.populationType"
            type="KEY_POPULATION_TYPE"
            :disabled="Boolean(applicationId)"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="关注等级" prop="attentionLevel">
          <el-select v-model="form.attentionLevel" :disabled="Boolean(applicationId)" style="width: 100%">
            <el-option v-for="(label, value) in ATTENTION_LEVEL" :key="value" :label="label" :value="value" />
          </el-select>
        </el-form-item>
        <el-form-item label="建档原因" prop="registerReason">
          <el-input v-model.trim="form.registerReason" type="textarea" :rows="3" :disabled="Boolean(applicationId)" />
        </el-form-item>
        <el-form-item label="建档日期" prop="registerDate">
          <el-date-picker
            v-model="form.registerDate"
            type="date"
            value-format="YYYY-MM-DD"
            :disabled="Boolean(applicationId)"
          />
        </el-form-item>
        <el-form-item label="责任部门 ID">
          <el-input-number v-model="form.responsibleDepartmentId" :min="1" :disabled="Boolean(applicationId)" />
        </el-form-item>
        <el-form-item label="责任人 ID">
          <el-input-number v-model="form.responsibleUserId" :min="1" :disabled="Boolean(applicationId)" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model.trim="form.remark" type="textarea" :rows="2" :disabled="Boolean(applicationId)" />
        </el-form-item>
        <el-form-item>
          <el-button v-if="!applicationId" type="primary" :loading="saving" @click="createDraft">创建草稿</el-button>
          <el-button v-if="applicationId" type="primary" @click="goDetail">前往申请详情</el-button>
          <el-button @click="router.push('/key-population')">返回列表</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import PersonSelect from '../../components/business/PersonSelect.vue'
import DictionarySelect from '../../components/business/DictionarySelect.vue'
import { createRegisterApplication } from '../../api/keyPopulation'
import { toCreateRegisterPayload } from '../../adapters/keyPopulation'
import { ATTENTION_LEVEL } from '../../constants/keyPopulation'
import { getApiErrorMessage } from '../../utils/apiError'
import { goBackOrFallback } from '../../utils/navigation'

const router = useRouter()
const formRef = ref()
const saving = ref(false)
const applicationId = ref(null)

const form = reactive({
  personId: null,
  populationType: '',
  attentionLevel: 'MEDIUM',
  registerReason: '',
  registerDate: '',
  responsibleDepartmentId: null,
  responsibleUserId: null,
  title: '',
  remark: ''
})

const rules = {
  title: [{ required: true, message: '请输入标题', trigger: 'blur' }],
  personId: [{ required: true, message: '请选择人员', trigger: 'change' }],
  populationType: [{ required: true, message: '请选择重点类型', trigger: 'change' }],
  attentionLevel: [{ required: true, message: '请选择关注等级', trigger: 'change' }],
  registerReason: [{ required: true, message: '请填写建档原因', trigger: 'blur' }],
  registerDate: [{ required: true, message: '请选择建档日期', trigger: 'change' }]
}

async function createDraft() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    const result = await createRegisterApplication(toCreateRegisterPayload(form))
    applicationId.value = result?.application?.applicationId || result?.applicationId || result
    ElMessage.success('建档草稿已创建，正在进入申请详情')
    // 创建成功立即进入统一申请详情，避免刷新创建页重复建草稿
    await router.replace(`/applications/${applicationId.value}`)
  } catch (error) {
    ElMessage.error(getApiErrorMessage(error, '创建建档申请失败'))
  } finally {
    saving.value = false
  }
}

function goDetail() {
  if (applicationId.value) router.push(`/applications/${applicationId.value}`)
}
</script>

<style scoped>
.page-container { display: flex; flex-direction: column; gap: 16px; }
.page-header { display: flex; justify-content: space-between; align-items: end; }
.page-header h1 { margin: 0 0 8px; }
.subtitle { margin: 0; color: var(--el-text-color-secondary); }
.form-card { max-width: 860px; }
</style>
