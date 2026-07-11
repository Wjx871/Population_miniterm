<template>
  <div class="page-container">
    <div class="page-header">
      <div class="header-left">
        <h1>证件信息管理</h1>
        <p class="subtitle">管理人员相关证件，支持到期自动预警与状态追踪。</p>
      </div>
      <div class="header-right">
        <el-button type="primary" :icon="Plus" @click="openCreateDialog" v-permission="'certificate:manage'">颁发/登记证件</el-button>
      </div>
    </div>

    <SearchPanel @search="fetchList" @reset="resetQuery">
      <el-form :inline="true" :model="query" size="default">
        <el-form-item label="证件类型">
          <el-select v-model="query.certificateType" placeholder="请选择证件类型" clearable style="width: 150px;">
            <el-option label="居民身份证" value="居民身份证" />
            <el-option label="居住证" value="居住证" />
            <el-option label="临时居住证" value="临时居住证" />
          </el-select>
        </el-form-item>
        <el-form-item label="证件状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 120px;">
            <el-option label="有效" value="有效" />
            <el-option label="即将到期" value="即将到期" />
            <el-option label="已过期" value="已过期" />
          </el-select>
        </el-form-item>
      </el-form>
    </SearchPanel>

    <el-card shadow="never" class="table-card">
      <el-table :data="tableData" v-loading="loading" border stripe style="width: 100%">
        <el-table-column prop="personName" label="持有者姓名" width="120" align="center" fixed />
        <el-table-column prop="certificateType" label="证件类型" width="130" align="center" />
        <el-table-column prop="certificateNo" label="证件编号" width="200" align="center" />
        <el-table-column prop="issueDate" label="签发日期" width="120" align="center">
          <template #default="{ row }">{{ formatDate(row.issueDate) }}</template>
        </el-table-column>
        <el-table-column prop="expireDate" label="有效期至" width="120" align="center">
          <template #default="{ row }">{{ formatDate(row.expireDate) }}</template>
        </el-table-column>
        <el-table-column prop="status" label="证件状态" width="120" align="center">
          <template #default="{ row }">
            <StatusTag :value="row.status" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" align="center" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" link @click="openEditDialog(row)" v-permission="'certificate:manage'">编辑</el-button>
            <el-button 
              size="small" 
              type="danger" 
              link 
              @click="handleDelete(row)"
              v-permission="'certificate:delete'"
            >
              作废
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
      :title="isEdit ? '编辑证件信息' : '登记新证件'"
      :loading="submitting"
      @confirm="submitForm"
    >
      <el-form 
        ref="formRef" 
        :model="form" 
        :rules="rules" 
        label-width="100px" 
      >
        <el-form-item label="持有人" prop="personId" v-if="!isEdit">
          <PersonSelect v-model="form.personId" />
        </el-form-item>
        <el-form-item label="持有人" v-else>
          <el-input v-model="form.personName" disabled />
        </el-form-item>
        <el-form-item label="证件类型" prop="certificateType">
          <el-select v-model="form.certificateType" style="width: 100%;">
            <el-option label="居民身份证" value="居民身份证" />
            <el-option label="居住证" value="居住证" />
            <el-option label="临时居住证" value="临时居住证" />
          </el-select>
        </el-form-item>
        <el-form-item label="证件编号" prop="certificateNo">
          <el-input v-model="form.certificateNo" placeholder="请输入证件编号" />
        </el-form-item>
        <el-form-item label="签发日期" prop="issueDate">
          <el-date-picker 
            v-model="form.issueDate" 
            type="date" 
            placeholder="请选择签发日期" 
            value-format="YYYY-MM-DD"
            style="width: 100%;"
          />
        </el-form-item>
        <el-form-item label="到期日期" prop="expireDate">
          <el-date-picker 
            v-model="form.expireDate" 
            type="date" 
            placeholder="请选择有效期至" 
            value-format="YYYY-MM-DD"
            style="width: 100%;"
          />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="form.status" style="width: 100%;">
            <el-option label="有效" value="有效" />
            <el-option label="即将到期" value="即将到期" />
            <el-option label="已过期" value="已过期" />
          </el-select>
        </el-form-item>
      </el-form>
    </FormDialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue';
import { Plus } from '@element-plus/icons-vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import SearchPanel from '../../components/common/SearchPanel.vue';
import AppPagination from '../../components/common/AppPagination.vue';
import FormDialog from '../../components/common/FormDialog.vue';
import StatusTag from '../../components/common/StatusTag.vue';
import PersonSelect from '../../components/business/PersonSelect.vue';
import { getCertificatePage, createCertificate, updateCertificate, deleteCertificate } from '../../api/certificates';
import { formatDate } from '../../utils/date';
import { normalizePageResult } from '../../utils/page';

const loading = ref(false);
const tableData = ref([]);
const total = ref(0);

const query = reactive({
  certificateType: '',
  status: '',
  current: 1,
  size: 10
});

const fetchList = async () => {
  loading.value = true;
  try {
    const res = await getCertificatePage(query);
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
  query.certificateType = '';
  query.status = '';
  query.current = 1;
  fetchList();
};

onMounted(() => {
  fetchList();
});

// Dialog
const dialogVisible = ref(false);
const isEdit = ref(false);
const submitting = ref(false);
const formRef = ref(null);

const form = reactive({
  id: null,
  personId: null,
  personName: '',
  certificateType: '居民身份证',
  certificateNo: '',
  issueDate: '',
  expireDate: '',
  status: '有效'
});

const rules = {
  personId: [{ required: true, message: '请选择持有人', trigger: 'change' }],
  certificateType: [{ required: true, message: '请选择证件类型', trigger: 'change' }],
  certificateNo: [{ required: true, message: '请输入证件编号', trigger: 'blur' }],
  issueDate: [{ required: true, message: '请选择签发日期', trigger: 'change' }],
  expireDate: [{ required: true, message: '请选择到期日期', trigger: 'change' }],
  status: [{ required: true, message: '请选择状态', trigger: 'change' }]
};

const openCreateDialog = () => {
  isEdit.value = false;
  Object.assign(form, {
    id: null,
    personId: null,
    personName: '',
    certificateType: '居民身份证',
    certificateNo: '',
    issueDate: '',
    expireDate: '',
    status: '有效'
  });
  dialogVisible.value = true;
  if (formRef.value) formRef.value.clearValidate();
};

const openEditDialog = (row) => {
  isEdit.value = true;
  Object.assign(form, {
    id: row.id || row.certificateId,
    personId: row.personId,
    personName: row.personName,
    certificateType: row.certificateType,
    certificateNo: row.certificateNo,
    issueDate: formatDate(row.issueDate),
    expireDate: formatDate(row.expireDate),
    status: row.status
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
        await updateCertificate(form.id, payload);
        ElMessage.success('修改成功');
      } else {
        await createCertificate(payload);
        ElMessage.success('登记成功');
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
  const id = row.id || row.certificateId;
  ElMessageBox.confirm(`确定要作废该证件吗？`, '警告', {
    confirmButtonText: '确定作废',
    cancelButtonText: '取消',
    type: 'warning',
  }).then(async () => {
    try {
      await deleteCertificate(id);
      ElMessage.success('作废成功');
      fetchList();
    } catch (error) {
      console.error(error);
    }
  }).catch(() => {});
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
