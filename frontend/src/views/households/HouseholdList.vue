<template>
  <div class="page-container">
    <div class="page-header">
      <div class="header-left">
        <h1>户籍管理</h1>
        <p class="subtitle">维护家庭户籍信息，支持户主设置与住址管理。</p>
      </div>
      <div class="header-right">
        <el-button type="primary" :icon="Plus" @click="openCreateDialog">开户立户</el-button>
      </div>
    </div>

    <SearchPanel @search="fetchList" @reset="resetQuery">
      <el-form :inline="true" :model="query" size="default">
        <el-form-item label="户籍编号">
          <el-input v-model="query.householdNo" placeholder="请输入户籍号" clearable />
        </el-form-item>
        <el-form-item label="户主姓名">
          <el-input v-model="query.headPersonName" placeholder="请输入户主姓名" clearable />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 120px;">
            <el-option label="正常" value="正常" />
            <el-option label="已撤销" value="已撤销" />
          </el-select>
        </el-form-item>
      </el-form>
    </SearchPanel>

    <el-card shadow="never" class="table-card">
      <el-table :data="tableData" v-loading="loading" border stripe style="width: 100%">
        <el-table-column prop="householdNo" label="户籍编号" width="150" align="center" fixed />
        <el-table-column prop="headPersonName" label="户主姓名" width="120" align="center" />
        <el-table-column prop="address" label="家庭住址" min-width="250" show-overflow-tooltip />
        <el-table-column prop="establishDate" label="立户日期" width="120" align="center">
          <template #default="{ row }">{{ formatDate(row.establishDate) }}</template>
        </el-table-column>
        <el-table-column prop="memberCount" label="家庭成员数" width="100" align="center">
          <template #default="{ row }">
            <el-tag size="small" type="info">{{ row.memberCount || 0 }} 人</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100" align="center">
          <template #default="{ row }">
            <StatusTag :value="row.status" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" align="center" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" link @click="viewDetail(row)">成员详情</el-button>
            <el-button size="small" type="primary" link @click="openEditDialog(row)">编辑</el-button>
            <el-button 
              size="small" 
              type="danger" 
              link 
              :disabled="row.status === '已撤销'"
              @click="handleDelete(row)"
            >
              撤销
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
      :title="isEdit ? '编辑户籍' : '新增户籍'"
      :loading="submitting"
      @confirm="submitForm"
    >
      <el-form 
        ref="formRef" 
        :model="form" 
        :rules="rules" 
        label-width="100px" 
      >
        <el-form-item label="户籍编号" prop="householdNo" v-if="isEdit">
          <el-input v-model="form.householdNo" disabled />
        </el-form-item>
        <el-form-item label="户主" prop="headPersonId">
          <PersonSelect v-model="form.headPersonId" />
        </el-form-item>
        <el-form-item label="户籍地址" prop="address">
          <el-input v-model="form.address" type="textarea" :rows="2" placeholder="请输入详细地址" />
        </el-form-item>
        <el-form-item label="立户日期" prop="establishDate">
          <el-date-picker 
            v-model="form.establishDate" 
            type="date" 
            placeholder="请选择日期" 
            value-format="YYYY-MM-DD"
            style="width: 100%;"
          />
        </el-form-item>
      </el-form>
    </FormDialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { Plus } from '@element-plus/icons-vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import SearchPanel from '../../components/common/SearchPanel.vue';
import AppPagination from '../../components/common/AppPagination.vue';
import FormDialog from '../../components/common/FormDialog.vue';
import StatusTag from '../../components/common/StatusTag.vue';
import PersonSelect from '../../components/business/PersonSelect.vue';
import { getHouseholdPage, createHousehold, updateHousehold, deleteHousehold } from '../../api/households';
import { formatDate } from '../../utils/date';
import { normalizePageResult } from '../../utils/page';

const router = useRouter();
const loading = ref(false);
const tableData = ref([]);
const total = ref(0);

const query = reactive({
  householdNo: '',
  headPersonName: '',
  status: '',
  current: 1,
  size: 10
});

const fetchList = async () => {
  loading.value = true;
  try {
    const res = await getHouseholdPage(query);
    const page = normalizePageResult(res);
    tableData.value = page.records;
    total.value = page.total;
  } catch (error) {
    console.error(error);
  } finally {
    loading.value = false;
  }
};

const resetQuery = () => {
  query.householdNo = '';
  query.headPersonName = '';
  query.status = '';
  query.current = 1;
  fetchList();
};

onMounted(() => {
  fetchList();
});

// 弹窗
const dialogVisible = ref(false);
const isEdit = ref(false);
const submitting = ref(false);
const formRef = ref(null);

const form = reactive({
  id: null,
  householdNo: '',
  headPersonId: null,
  address: '',
  establishDate: ''
});

const rules = {
  headPersonId: [{ required: true, message: '请选择户主', trigger: 'change' }],
  address: [{ required: true, message: '请输入户籍地址', trigger: 'blur' }],
  establishDate: [{ required: true, message: '请选择立户日期', trigger: 'change' }]
};

const openCreateDialog = () => {
  isEdit.value = false;
  Object.assign(form, {
    id: null,
    householdNo: '',
    headPersonId: null,
    address: '',
    establishDate: ''
  });
  dialogVisible.value = true;
  if (formRef.value) formRef.value.clearValidate();
};

const openEditDialog = (row) => {
  isEdit.value = true;
  Object.assign(form, {
    id: row.id || row.householdId,
    householdNo: row.householdNo,
    headPersonId: row.headPersonId,
    address: row.address,
    establishDate: formatDate(row.establishDate)
  });
  dialogVisible.value = true;
  if (formRef.value) formRef.value.clearValidate();
};

const submitForm = () => {
  if (!formRef.value) return;
  formRef.value.validate(async (valid) => {
    if (!valid) return;
    
    submitting.value = true;
    try {
      const payload = { ...form };
      if (isEdit.value) {
        await updateHousehold(form.id, payload);
        ElMessage.success('修改成功');
      } else {
        await createHousehold(payload);
        ElMessage.success('新增成功');
      }
      dialogVisible.value = false;
      fetchList();
    } catch (error) {
      console.error(error);
    } finally {
      submitting.value = false;
    }
  });
};

const handleDelete = (row) => {
  const id = row.id || row.householdId;
  ElMessageBox.confirm(`确定要撤销户籍 [${row.householdNo}] 吗？`, '警告', {
    confirmButtonText: '确定撤销',
    cancelButtonText: '取消',
    type: 'warning',
  }).then(async () => {
    try {
      await deleteHousehold(id);
      ElMessage.success('撤销成功');
      fetchList();
    } catch (error) {
      console.error(error);
    }
  }).catch(() => {});
};

const viewDetail = (row) => {
  const id = row.id || row.householdId;
  router.push(`/households/${id}`);
};
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
