<template>
  <div class="page-container">
    <div class="page-header">
      <div class="header-left">
        <h1>人口信息管理</h1>
        <p class="subtitle">维护人口基础信息，支持多条件查询与档案管理。</p>
      </div>
      <div class="header-right">
        <el-button
          type="primary"
          :icon="Plus"
          v-permission="'population:edit'"
          @click="openCreateDialog"
        >
          新增人员
        </el-button>
      </div>
    </div>

    <SearchPanel @search="handleSearch" @reset="resetQuery">
      <el-form :inline="true" :model="query" size="default">
        <el-form-item label="姓名">
          <el-input v-model="query.name" placeholder="请输入姓名" clearable />
        </el-form-item>
        <el-form-item label="身份证号">
          <el-input v-model="query.idCard" placeholder="请输入身份证号" clearable />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 120px;">
            <el-option label="正常" value="正常" />
            <el-option label="已注销" value="已注销" />
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
        <el-table-column prop="name" label="姓名" width="100" fixed />
        <el-table-column prop="gender" label="性别" width="80" align="center" />
        <el-table-column label="身份证号" width="200" align="center">
          <template #default="{ row }">
            <SensitiveText :value="row.idCard" kind="idCard" :revealable="false" />
          </template>
        </el-table-column>
        <el-table-column prop="birthDate" label="出生日期" width="120" align="center">
          <template #default="{ row }">{{ formatDate(row.birthDate) }}</template>
        </el-table-column>
        <el-table-column prop="ethnicity" label="民族" width="100" align="center" />
        <el-table-column label="联系电话" width="140" align="center">
          <template #default="{ row }">
            <SensitiveText :value="row.phone" kind="phone" :revealable="false" />
          </template>
        </el-table-column>
        <el-table-column
          prop="currentAddress"
          label="现居住地址"
          min-width="200"
          show-overflow-tooltip
        />
        <el-table-column prop="status" label="状态" width="100" align="center">
          <template #default="{ row }">
            <StatusTag :value="row.status" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" align="center" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" link @click="openDetail(row)">
              详情
            </el-button>
            <el-button
              size="small"
              type="primary"
              link
              v-permission="'population:edit'"
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
      :title="isEdit ? '编辑人口信息' : '新增人口信息'"
      :loading="submitting"
      @confirm="submitForm"
    >
      <PersonForm
        ref="personFormRef"
        v-model="formModel"
        :is-edit="isEdit"
      />
    </FormDialog>

    <PersonDetailDrawer
      v-model="detailVisible"
      :person-id="detailPersonId"
    />
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import SearchPanel from '../../components/common/SearchPanel.vue'
import AppPagination from '../../components/common/AppPagination.vue'
import FormDialog from '../../components/common/FormDialog.vue'
import StatusTag from '../../components/common/StatusTag.vue'
import SensitiveText from '../../components/common/SensitiveText.vue'
import PersonForm from './components/PersonForm.vue'
import PersonDetailDrawer from './components/PersonDetailDrawer.vue'
import { getPersonPage, getPersonById, createPerson, updatePerson } from '../../api/persons'
import {
  normalizePerson,
  normalizePersonList,
  toCreatePersonPayload,
  toUpdatePersonPayload,
} from '../../adapters/person'
import { formatDate } from '../../utils/date'
import { normalizePageResult } from '../../utils/page'

const loading = ref(false)
const tableData = ref([])
const total = ref(0)

const query = reactive({
  name: '',
  idCard: '',
  status: '',
  current: 1,
  size: 10,
})

const dialogVisible = ref(false)
const isEdit = ref(false)
const submitting = ref(false)
const personFormRef = ref(null)
const formModel = ref({})
/** 编辑时缓存最新详情，用于构造 update payload 的 idCard/status */
const latestDetail = ref(null)
const editingId = ref(null)

const detailVisible = ref(false)
const detailPersonId = ref(null)

const fetchList = async () => {
  loading.value = true
  try {
    const res = await getPersonPage({
      name: query.name,
      idCard: query.idCard,
      status: query.status,
      current: query.current,
      size: query.size,
    })
    const page = normalizePageResult(res)
    tableData.value = normalizePersonList(page.records)
    total.value = page.total
  } catch (error) {
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
  query.name = ''
  query.idCard = ''
  query.status = ''
  query.current = 1
  fetchList()
}

const openCreateDialog = () => {
  isEdit.value = false
  editingId.value = null
  latestDetail.value = null
  formModel.value = {
    name: '',
    gender: '男',
    idCard: '',
    birthDate: '',
    ethnicity: '汉族',
    phone: '',
    currentAddress: '',
  }
  dialogVisible.value = true
  personFormRef.value?.clearValidate()
}

const openEditDialog = async (row) => {
  if (!row?.id) {
    ElMessage.warning('无法识别人员标识')
    return
  }
  isEdit.value = true
  editingId.value = row.id
  submitting.value = true
  dialogVisible.value = true
  try {
    // 强制请求最新详情，不信任列表行
    const res = await getPersonById(row.id)
    const detail = normalizePerson(res)
    latestDetail.value = detail
    formModel.value = {
      name: detail.name,
      gender: detail.gender || '男',
      idCard: detail.idCard,
      birthDate: formatDate(detail.birthDate),
      ethnicity: detail.ethnicity || '',
      phone: detail.phone || '',
      currentAddress: detail.currentAddress || '',
    }
    personFormRef.value?.clearValidate()
  } catch (error) {
    console.error(error)
    dialogVisible.value = false
    latestDetail.value = null
  } finally {
    submitting.value = false
  }
}

const openDetail = (row) => {
  detailPersonId.value = row.id
  detailVisible.value = true
}

const submitForm = async () => {
  if (!personFormRef.value) return
  const valid = await personFormRef.value.validate()
  if (!valid) return

  const form = personFormRef.value.getForm()
  if (!isEdit.value && !form.idCardImageId) {
    ElMessage.error('新增人口必须先上传身份证影印本')
    return
  }
  submitting.value = true
  try {
    if (isEdit.value) {
      if (!latestDetail.value || !editingId.value) {
        ElMessage.error('缺少最新详情，请重新打开编辑')
        return
      }
      // idCard / status 取自最新详情对象，不依赖可被修改的隐藏表单字段；
      // status 缺失时适配器会抛错，禁止兜底为「正常」
      const payload = toUpdatePersonPayload(form, latestDetail.value)
      await updatePerson(editingId.value, payload)
      ElMessage.success('修改成功')
    } else {
      const payload = toCreatePersonPayload(form)
      await createPerson(payload)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    fetchList()
  } catch (error) {
    if (error?.message && String(error.message).includes('最新详情缺少')) {
      ElMessage.error(error.message)
    }
    console.error(error)
  } finally {
    submitting.value = false
  }
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
.idcard-block {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 100%;
}
.idcard-block .idcard-tag {
  margin-left: 12px;
}
.idcard-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  font-size: 13px;
  color: var(--el-text-color-regular);
}
.idcard-hint {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}
</style>
