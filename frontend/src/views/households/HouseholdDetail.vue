<template>
  <div class="page-container">
    <div class="page-header">
      <div class="header-left">
        <el-button link :icon="ArrowLeft" style="margin-bottom: 8px;" @click="goBack">
          返回列表
        </el-button>
        <h1>户籍详情：{{ householdInfo.householdNo || '加载中...' }}</h1>
        <p class="subtitle">
          家庭住址：{{ householdInfo.address || '-' }}
          ｜ 户主：{{ householdInfo.headPersonName || '-' }}
        </p>
      </div>
      <div class="header-right">
        <el-button
          type="primary"
          :icon="Plus"
          v-permission="'household:edit'"
          @click="openAddDialog"
        >
          添加成员
        </el-button>
        <el-button v-permission="'household:edit'" :disabled="!headCandidates.length" @click="openHeadDialog">
          变更户主
        </el-button>
      </div>
    </div>

    <el-card shadow="never" class="overview-card" v-loading="infoLoading">
      <template #header>
        <span>家庭户概览</span>
      </template>
      <el-descriptions :column="3" border>
        <el-descriptions-item label="户籍编号">
          {{ householdInfo.householdNo || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="户主">
          {{ householdInfo.headPersonName || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="状态">
          <StatusTag v-if="householdInfo.status" :value="householdInfo.status" />
          <span v-else>-</span>
        </el-descriptions-item>
        <el-descriptions-item label="立户日期">
          {{ formatDate(householdInfo.establishDate) || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="成员数量">
          {{ members.length || householdInfo.memberCount || 0 }}
        </el-descriptions-item>
        <el-descriptions-item label="户籍地址" :span="3">
          {{ householdInfo.address || '-' }}
        </el-descriptions-item>
      </el-descriptions>
    </el-card>

    <el-card shadow="never" class="table-card">
      <template #header>
        <span>当前成员</span>
      </template>
      <el-table
        :data="members"
        v-loading="membersLoading"
        border
        stripe
        style="width: 100%"
        row-key="memberId"
      >
        <el-table-column prop="personName" label="姓名" width="120" />
        <el-table-column prop="relationship" label="与户主关系" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="row.isHead ? 'primary' : 'info'" size="small">
              {{ row.relationship || (row.isHead ? '户主' : '-') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="身份证号" width="200" align="center">
          <template #default="{ row }">
            <SensitiveText :value="row.idCard" kind="idCard" :revealable="false" />
          </template>
        </el-table-column>
        <el-table-column label="联系电话" width="140" align="center">
          <template #default="{ row }">
            <SensitiveText :value="row.phone" kind="phone" :revealable="false" />
          </template>
        </el-table-column>
        <el-table-column prop="joinDate" label="加入日期" width="130" align="center">
          <template #default="{ row }">{{ formatDate(row.joinDate) }}</template>
        </el-table-column>
        <el-table-column label="是否户主" width="100" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.isHead" type="success" size="small">户主</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" align="center" fixed="right">
          <template #default="{ row }">
            <el-button
              size="small"
              type="danger"
              link
              :disabled="row.isHead"
              v-permission="'household:edit'"
              @click="handleRemove(row)"
            >
              移出
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <FormDialog
      v-model:visible="dialogVisible"
      title="添加户籍成员"
      :loading="submitting"
      @confirm="submitForm"
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="100px"
      >
        <el-form-item label="选择成员" prop="personId">
          <PersonSelect
            v-model="form.personId"
            status="正常"
            :exclude-ids="excludePersonIds"
          />
        </el-form-item>
        <el-form-item label="与户主关系" prop="relationship">
          <el-select v-model="form.relationship" style="width: 100%">
            <el-option label="配偶" value="SPOUSE" />
            <el-option label="子女" value="CHILD" />
            <el-option label="父母" value="PARENT" />
            <el-option label="其他" value="OTHER" />
          </el-select>
        </el-form-item>
        <el-form-item label="加入日期" prop="joinDate">
          <el-date-picker
            v-model="form.joinDate"
            type="date"
            placeholder="请选择日期"
            value-format="YYYY-MM-DD"
            :disabled-date="disableFutureDate"
            style="width: 100%;"
          />
        </el-form-item>
      </el-form>
    </FormDialog>

    <FormDialog v-model:visible="headDialogVisible" title="变更户主" :loading="headSubmitting" @confirm="submitHeadChange">
      <el-form ref="headFormRef" :model="headForm" :rules="headRules" label-width="100px">
        <el-form-item label="新户主" prop="newHeadPersonId">
          <el-select v-model="headForm.newHeadPersonId" placeholder="请选择当前有效成员" style="width:100%">
            <el-option v-for="member in headCandidates" :key="member.personId" :label="member.personName" :value="member.personId" />
          </el-select>
        </el-form-item>
        <el-form-item label="变更原因" prop="reason">
          <el-input v-model.trim="headForm.reason" type="textarea" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
    </FormDialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, Plus } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import dayjs from 'dayjs'
import FormDialog from '../../components/common/FormDialog.vue'
import StatusTag from '../../components/common/StatusTag.vue'
import SensitiveText from '../../components/common/SensitiveText.vue'
import PersonSelect from '../../components/business/PersonSelect.vue'
import {
  getHouseholdById,
  getHouseholdMembers,
  addHouseholdMember,
  leaveHouseholdMember,
  changeHouseholdHead,
} from '../../api/households'
import {
  normalizeHousehold,
  normalizeHouseholdMembers,
} from '../../adapters/household'
import { formatDate } from '../../utils/date'

const route = useRoute()
const router = useRouter()
const householdId = route.params.id

const infoLoading = ref(false)
const membersLoading = ref(false)
const householdInfo = ref(normalizeHousehold(null))
const members = ref([])
const headDialogVisible = ref(false)
const headSubmitting = ref(false)
const headFormRef = ref(null)
const headForm = reactive({ newHeadPersonId: null, reason: '' })
const headRules = {
  newHeadPersonId: [{ required: true, message: '请选择新户主', trigger: 'change' }],
  reason: [{ required: true, message: '请输入变更原因', trigger: 'blur' }],
}
const headCandidates = computed(() => members.value.filter((member) => !member.isHead && member.status === 'ACTIVE'))

const dialogVisible = ref(false)
const submitting = ref(false)
const formRef = ref(null)

const form = reactive({
  personId: null,
  relationship: '',
  joinDate: dayjs().format('YYYY-MM-DD'),
})

const rules = {
  personId: [{ required: true, message: '请选择人员', trigger: 'change' }],
  relationship: [{ required: true, message: '请选择与户主关系', trigger: 'change' }],
  joinDate: [{ required: true, message: '请选择加入日期', trigger: 'change' }],
}

const excludePersonIds = computed(() => {
  const ids = members.value
    .map((m) => m.personId)
    .filter((id) => id != null)
  if (householdInfo.value.headPersonId != null) {
    ids.push(householdInfo.value.headPersonId)
  }
  return ids
})

function disableFutureDate(date) {
  const today = new Date()
  today.setHours(23, 59, 59, 999)
  return date.getTime() > today.getTime()
}

function goBack() {
  router.push('/households')
}

async function fetchHouseholdInfo() {
  infoLoading.value = true
  try {
    const res = await getHouseholdById(householdId)
    householdInfo.value = normalizeHousehold(res)
  } catch (error) {
    console.error(error)
    householdInfo.value = normalizeHousehold(null)
  } finally {
    infoLoading.value = false
  }
}

async function fetchMembers() {
  membersLoading.value = true
  try {
    const res = await getHouseholdMembers(householdId)
    const list = Array.isArray(res) ? res : []
    members.value = normalizeHouseholdMembers(list, householdInfo.value.headPersonId)
  } catch (error) {
    console.error(error)
    members.value = []
  } finally {
    membersLoading.value = false
  }
}

async function loadAll() {
  await fetchHouseholdInfo()
  await fetchMembers()
}

const openAddDialog = () => {
  Object.assign(form, {
    personId: null,
    relationship: '',
    joinDate: dayjs().format('YYYY-MM-DD'),
  })
  dialogVisible.value = true
  formRef.value?.clearValidate()
}

function openHeadDialog() {
  Object.assign(headForm, { newHeadPersonId: null, reason: '' })
  headDialogVisible.value = true
  headFormRef.value?.clearValidate()
}

async function submitHeadChange() {
  if (!await headFormRef.value?.validate()) return
  headSubmitting.value = true
  try {
    await changeHouseholdHead(householdId, {
      newHeadPersonId: headForm.newHeadPersonId,
      reason: headForm.reason,
      version: householdInfo.value.version,
    })
    ElMessage.success('户主变更成功')
    headDialogVisible.value = false
    await loadAll()
  } finally {
    headSubmitting.value = false
  }
}

const submitForm = () => {
  if (!formRef.value) return
  formRef.value.validate(async (valid) => {
    if (!valid) return

    submitting.value = true
    try {
      await addHouseholdMember(householdId, {
        personId: form.personId,
        relationship: form.relationship,
        joinDate: form.joinDate,
      })
      ElMessage.success('添加成员成功')
      dialogVisible.value = false
      await fetchMembers()
      // 成员数可能变化，尝试刷新概览
      fetchHouseholdInfo()
    } catch (error) {
      console.error(error)
    } finally {
      submitting.value = false
    }
  })
}

const handleRemove = (row) => {
  if (row.isHead) {
    ElMessage.warning('户主不能通过移出成员操作移除')
    return
  }
  const memberId = row.memberId
  if (!memberId) {
    ElMessage.warning('无法识别成员标识')
    return
  }

  const householdNo = householdInfo.value.householdNo || householdId
  ElMessageBox.confirm(
    `确定将成员「${row.personName || '未知'}」从家庭户「${householdNo}」中移出吗？\n此操作仅适用于无当前户籍的家庭关系纠错；有当前有效户籍的人员请通过迁出或人员注销业务办理。`,
    '移出家庭成员',
    {
      confirmButtonText: '确定移出',
      cancelButtonText: '取消',
      type: 'warning',
    }
  ).then(async () => {
    try {
      await leaveHouseholdMember(householdId, memberId, {
        leaveDate: dayjs().format('YYYY-MM-DD'),
        version: row.version,
      })
      ElMessage.success('移出成功')
      await fetchMembers()
      fetchHouseholdInfo()
    } catch (error) {
      console.error(error)
    }
  }).catch(() => {})
}

onMounted(() => {
  loadAll()
})
</script>

<style scoped>
.page-container {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
}
.page-header h1 {
  margin: 0 0 8px 0;
  font-size: 20px;
}
.subtitle {
  margin: 0;
  color: var(--color-ink-muted);
  font-size: 14px;
}
.overview-card,
.table-card {
  border-radius: var(--radius-large);
}
</style>
