<template>
  <div class="page-container">
    <div class="page-header">
      <div class="header-left">
        <h1>户籍管理</h1>
        <p class="subtitle">维护家庭户籍信息，支持立户与基础信息管理。</p>
      </div>
      <div class="header-right">
        <el-button
          type="primary"
          :icon="Plus"
          v-permission="'household:edit'"
          @click="openCreateDialog"
        >
          开户立户
        </el-button>
      </div>
    </div>

    <SearchPanel @search="handleSearch" @reset="resetQuery">
      <el-form :inline="true" :model="query" size="default">
        <el-form-item label="户籍编号">
          <el-input v-model="query.householdNo" placeholder="请输入户籍号" clearable />
        </el-form-item>
        <el-form-item label="户主姓名">
          <el-input v-model="query.headPersonName" placeholder="请输入户主姓名" clearable />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 120px;">
            <el-option label="有效" value="ACTIVE" />
            <el-option label="待注销" value="PENDING_CANCELLATION" />
            <el-option label="已归档" value="ARCHIVED" />
            <el-option label="已注销" value="CANCELLED" />
          </el-select>
        </el-form-item>
      </el-form>
    </SearchPanel>

    <el-card shadow="never" class="table-card">
      <el-table
        :data="tableData"
        v-loading="loading"
        border
        stripe
        style="width: 100%"
        row-key="id"
      >
        <el-table-column prop="householdNo" label="户籍编号" width="150" align="center" fixed />
        <el-table-column prop="headPersonName" label="户主姓名" width="120" align="center" />
        <el-table-column prop="address" label="家庭住址" min-width="250" show-overflow-tooltip />
        <el-table-column prop="establishDate" label="立户日期" width="120" align="center">
          <template #default="{ row }">{{ formatDate(row.establishDate) }}</template>
        </el-table-column>
        <el-table-column prop="memberCount" label="家庭成员数" width="110" align="center">
          <template #default="{ row }">
            <el-tag size="small" type="info">{{ row.memberCount || 0 }} 人</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="130" align="center" show-overflow-tooltip>
          <template #default="{ row }">
            <StatusTag :value="row.status" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" align="center" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" link @click="viewDetail(row)">
              详情
            </el-button>
            <el-button
              size="small"
              type="primary"
              link
              v-permission="'household:edit'"
              @click="openEditDialog(row)"
            >
              编辑
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <AppPagination
        v-model:current="query.current"
        v-model:size="query.size"
        :total="total"
        @change="fetchList"
      />
    </el-card>

    <FormDialog
      v-model:visible="dialogVisible"
      :title="isEdit ? '编辑户籍基础信息' : '开户立户'"
      :loading="submitting"
      @confirm="submitForm"
    >
      <HouseholdForm
        ref="householdFormRef"
        v-model="formModel"
        :is-edit="isEdit"
        :household-no="editMeta.householdNo"
        :head-person-name="editMeta.headPersonName"
        :head-person-id="editMeta.headPersonId"
        :status="editMeta.status"
      />
    </FormDialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { Plus } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import SearchPanel from '../../components/common/SearchPanel.vue'
import AppPagination from '../../components/common/AppPagination.vue'
import FormDialog from '../../components/common/FormDialog.vue'
import StatusTag from '../../components/common/StatusTag.vue'
import HouseholdForm from './components/HouseholdForm.vue'
import {
  getHouseholdPage,
  getHouseholdById,
  createHousehold,
  updateHousehold,
} from '../../api/households'
import {
  normalizeHousehold,
  normalizeHouseholdList,
  toCreateHouseholdPayload,
  toUpdateHouseholdPayload,
} from '../../adapters/household'
import { formatDate } from '../../utils/date'
import { normalizePageResult } from '../../utils/page'

const router = useRouter()
const loading = ref(false)
const tableData = ref([])
const total = ref(0)

const query = reactive({
  householdNo: '',
  headPersonName: '',
  status: '',
  current: 1,
  size: 10,
})

const dialogVisible = ref(false)
const isEdit = ref(false)
const submitting = ref(false)
const householdFormRef = ref(null)
const formModel = ref({})
const editingId = ref(null)
const editMeta = reactive({
  householdNo: '',
  headPersonName: '',
  headPersonId: null,
  status: '',
  version: null,
})

const fetchList = async () => {
  loading.value = true
  try {
    const res = await getHouseholdPage({
      householdNo: query.householdNo,
      headPersonName: query.headPersonName,
      status: query.status,
      current: query.current,
      size: query.size,
    })
    const page = normalizePageResult(res)
    tableData.value = normalizeHouseholdList(page.records)
    total.value = page.total
  } catch (error) {
    // 接口契约待后端确认；失败时保留空表，不注入假数据
    console.error(error)
    tableData.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  query.current = 1
  fetchList()
}

const resetQuery = () => {
  query.householdNo = ''
  query.headPersonName = ''
  query.status = ''
  query.current = 1
  fetchList()
}

const openCreateDialog = () => {
  isEdit.value = false
  editingId.value = null
  Object.assign(editMeta, {
    householdNo: '',
    headPersonName: '',
    headPersonId: null,
    status: '',
    version: null,
  })
  formModel.value = {
    householdNo: '',
    headPersonId: null,
    address: '',
    regionCode: '',
    householdType: '',
    establishDate: '',
  }
  dialogVisible.value = true
  householdFormRef.value?.clearValidate()
}

const openEditDialog = async (row) => {
  if (!row?.id) {
    ElMessage.warning('无法识别家庭户标识')
    return
  }
  isEdit.value = true
  editingId.value = row.id
  submitting.value = true
  dialogVisible.value = true
  try {
    const res = await getHouseholdById(row.id)
    const detail = normalizeHousehold(res)

    Object.assign(editMeta, {
      householdNo: detail.householdNo,
      headPersonName: detail.headPersonName,
      headPersonId: detail.headPersonId,
      status: detail.status,
      version: detail.version,
    })
    formModel.value = {
      householdNo: detail.householdNo,
      address: detail.address,
      regionCode: detail.regionCode,
      householdType: detail.householdType,
      establishDate: formatDate(detail.establishDate),
    }
    householdFormRef.value?.clearValidate()
  } catch (error) {
    console.error(error)
    dialogVisible.value = false
  } finally {
    submitting.value = false
  }
}

const submitForm = async () => {
  if (!householdFormRef.value) return
  const valid = await householdFormRef.value.validate()
  if (!valid) return

  const form = householdFormRef.value.getForm()
  submitting.value = true
  try {
    if (isEdit.value) {
      // 基础编辑不含户主与状态
      const payload = toUpdateHouseholdPayload(form, editMeta)
      await updateHousehold(editingId.value, payload)
      ElMessage.success('修改成功')
    } else {
      const payload = toCreateHouseholdPayload(form)
      await createHousehold(payload)
      ElMessage.success('立户成功')
    }
    dialogVisible.value = false
    fetchList()
  } catch (error) {
    console.error(error)
  } finally {
    submitting.value = false
  }
}

const viewDetail = (row) => {
  if (!row?.id) {
    ElMessage.warning('无法识别家庭户标识')
    return
  }
  router.push(`/households/${row.id}`)
}

onMounted(() => {
  fetchList()
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
.table-card {
  border-radius: var(--radius-large);
}
</style>
