<template>
  <div class="page-container">
    <div class="page-header">
      <div class="header-left">
        <h1>人口信息管理</h1>
        <p class="subtitle">维护人口基础信息，支持多条件查询与档案管理。</p>
      </div>
      <div class="header-right">
        <el-button type="primary" :icon="Plus" @click="openCreateDialog">新增人员</el-button>
      </div>
    </div>

    <SearchPanel @search="fetchList" @reset="resetQuery">
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
      <el-table :data="tableData" v-loading="loading" border stripe style="width: 100%">
        <el-table-column prop="name" label="姓名" width="100" fixed />
        <el-table-column prop="gender" label="性别" width="80" align="center" />
        <el-table-column prop="idCard" label="身份证号" width="180" align="center" />
        <el-table-column prop="birthDate" label="出生日期" width="120" align="center">
          <template #default="{ row }">{{ formatDate(row.birthDate) }}</template>
        </el-table-column>
        <el-table-column prop="ethnicity" label="民族" width="100" align="center" />
        <el-table-column prop="phone" label="联系电话" width="130" align="center" />
        <el-table-column prop="currentAddress" label="现居住地址" min-width="200" show-overflow-tooltip />
        <el-table-column prop="status" label="状态" width="100" align="center">
          <template #default="{ row }">
            <StatusTag :value="row.status" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" align="center" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" link @click="openEditDialog(row)">编辑</el-button>
            <el-button 
              size="small" 
              type="danger" 
              link 
              :disabled="row.status === '已注销'"
              @click="handleDelete(row)"
            >
              删除
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
      <el-form 
        ref="formRef" 
        :model="form" 
        :rules="rules" 
        label-width="100px" 
      >
        <el-form-item label="姓名" prop="name">
          <el-input v-model="form.name" placeholder="请输入姓名" />
        </el-form-item>
        <el-form-item label="性别" prop="gender">
          <el-radio-group v-model="form.gender">
            <el-radio value="男">男</el-radio>
            <el-radio value="女">女</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="身份证号" prop="idCard">
          <el-input v-model="form.idCard" placeholder="请输入身份证号" :disabled="isEdit" />
        </el-form-item>
        <el-form-item label="出生日期" prop="birthDate">
          <el-date-picker 
            v-model="form.birthDate" 
            type="date" 
            placeholder="请选择日期" 
            value-format="YYYY-MM-DD"
            style="width: 100%;"
          />
        </el-form-item>
        <el-form-item label="民族" prop="ethnicity">
          <el-input v-model="form.ethnicity" placeholder="如：汉族" />
        </el-form-item>
        <el-form-item label="联系电话" prop="phone">
          <el-input v-model="form.phone" placeholder="手机号" />
        </el-form-item>
        <el-form-item label="现居住地址" prop="currentAddress">
          <el-input v-model="form.currentAddress" type="textarea" :rows="2" placeholder="请输入详细地址" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="form.status" style="width: 100%;">
            <el-option label="正常" value="正常" />
            <el-option label="已注销" value="已注销" />
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
import { getPersonPage, createPerson, updatePerson, deletePerson } from '../../api/persons';
import { formatDate } from '../../utils/date';
import { validateIdCard, validatePhone } from '../../utils/validators';

const loading = ref(false);
const tableData = ref([]);
const total = ref(0);

const query = reactive({
  name: '',
  idCard: '',
  status: '',
  current: 1,
  size: 10
});

const fetchList = async () => {
  loading.value = true;
  try {
    const res = await getPersonPage({
      name: query.name,
      idCard: query.idCard,
      status: query.status,
      current: query.current,
      size: query.size
    });
    // Spring Boot page is typically { content, totalElements } or { records, total }
    tableData.value = res.records || res.content || [];
    total.value = res.total || res.totalElements || 0;
  } catch (error) {
    console.error(error);
  } finally {
    loading.value = false;
  }
};

const resetQuery = () => {
  query.name = '';
  query.idCard = '';
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
  name: '',
  gender: '男',
  idCard: '',
  birthDate: '',
  ethnicity: '汉族',
  phone: '',
  currentAddress: '',
  status: '正常'
});

const rules = {
  name: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  gender: [{ required: true, message: '请选择性别', trigger: 'change' }],
  idCard: [
    { required: true, message: '请输入身份证号', trigger: 'blur' },
    { validator: validateIdCard, trigger: 'blur' }
  ],
  birthDate: [{ required: true, message: '请选择出生日期', trigger: 'change' }],
  phone: [{ validator: validatePhone, trigger: 'blur' }]
};

const openCreateDialog = () => {
  isEdit.value = false;
  Object.assign(form, {
    id: null,
    name: '',
    gender: '男',
    idCard: '',
    birthDate: '',
    ethnicity: '汉族',
    phone: '',
    currentAddress: '',
    status: '正常'
  });
  dialogVisible.value = true;
  if (formRef.value) formRef.value.clearValidate();
};

const openEditDialog = (row) => {
  isEdit.value = true;
  Object.assign(form, {
    id: row.id || row.personId,
    name: row.name,
    gender: row.gender,
    idCard: row.idCard,
    birthDate: formatDate(row.birthDate),
    ethnicity: row.ethnicity,
    phone: row.phone,
    currentAddress: row.currentAddress,
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
        await updatePerson(form.id, payload);
        ElMessage.success('修改成功');
      } else {
        await createPerson(payload);
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
  const id = row.id || row.personId;
  ElMessageBox.confirm(`确定要删除人员 [${row.name}] 吗？`, '警告', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning',
  }).then(async () => {
    try {
      await deletePerson(id);
      ElMessage.success('删除成功');
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
