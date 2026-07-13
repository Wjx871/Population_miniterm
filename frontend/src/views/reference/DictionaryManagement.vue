<template>
  <div class="page-container">
    <div class="page-header">
      <div class="header-left">
        <h1>数据字典管理</h1>
        <p class="subtitle">管理系统各类业务下拉选项。禁用的选项不会在新建表单中出现。</p>
      </div>
      <div class="header-right">
        <el-button type="primary" :icon="Plus" @click="openCreateDialog">新增字典项</el-button>
      </div>
    </div>

    <SearchPanel @search="fetchList" @reset="resetQuery">
      <el-form :inline="true" :model="query" size="default">
        <el-form-item label="字典类型">
          <el-input v-model.trim="query.dictType" placeholder="如 CERTIFICATE_TYPE" clearable />
        </el-form-item>
        <el-form-item label="字典状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 120px;">
            <el-option label="有效" value="ENABLED" />
            <el-option label="禁用" value="DISABLED" />
          </el-select>
        </el-form-item>
      </el-form>
    </SearchPanel>

    <el-card shadow="never" class="table-card">
      <el-table :data="tableData" v-loading="loading" border stripe style="width: 100%">
        <el-table-column prop="dictType" label="字典类型" width="180" align="center" fixed />
        <el-table-column prop="dictCode" label="字典编码" width="180" align="center" />
        <el-table-column prop="dictName" label="显示标签" min-width="150" align="center" />
        <el-table-column prop="sortNo" label="排序权重" width="100" align="center" />
        <el-table-column prop="status" label="状态" width="100" align="center">
          <template #default="{ row }">
            <StatusTag :value="row.status" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" align="center" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="PERMISSIONS.DICTIONARY_MANAGE" size="small" type="primary" link @click="openEditDialog(row)">编辑</el-button>
            <el-button 
              v-permission="PERMISSIONS.DICTIONARY_MANAGE"
              size="small" 
              :type="row.status === 'ENABLED' ? 'danger' : 'success'"
              link 
              @click="toggleStatus(row)"
            >
              {{ row.status === 'ENABLED' ? '禁用' : '启用' }}
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
      :title="isEdit ? '编辑字典项' : '新增字典项'"
      :loading="submitting"
      @confirm="submitForm"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="字典类型" prop="dictType">
          <el-input v-model="form.dictType" :disabled="isEdit" placeholder="如 CERTIFICATE_TYPE" />
        </el-form-item>
        <el-form-item label="字典编码" prop="dictCode">
          <el-input v-model="form.dictCode" :disabled="isEdit" placeholder="唯一编码" />
        </el-form-item>
        <el-form-item label="显示标签" prop="dictName">
          <el-input v-model="form.dictName" placeholder="UI显示名称" />
        </el-form-item>
        <el-form-item label="排序权重" prop="sortNo">
          <el-input-number v-model="form.sortNo" :min="0" :max="999" />
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
import { getDictionaryPage, getDictionaryDetail, createDictionaryItem, updateDictionaryItem, enableDictionaryItem, disableDictionaryItem } from '../../api/dictionaries';
import { clearAllReferenceCache } from '../../services/referenceDataCache';
import { normalizePageResult } from '../../utils/page';
import { getApiErrorMessage, isApiConflict } from '../../utils/apiError';
import { PERMISSIONS } from '../../constants/permissions';

const loading = ref(false);
const tableData = ref([]);
const total = ref(0);

const query = reactive({
  dictType: '',
  status: '',
  current: 1,
  size: 10
});

const fetchList = async () => {
  loading.value = true;
  try {
    const res = await getDictionaryPage(query);
    const page = normalizePageResult(res);
    tableData.value = page.records;
    total.value = page.total;
  } catch (error) {
    ElMessage.error(getApiErrorMessage(error, '加载列表失败'));
  } finally {
    loading.value = false;
  }
};

const resetQuery = () => {
  query.dictType = '';
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
  dictType: '',
  dictCode: '',
  dictName: '',
  sortNo: 0,
  version: 0
});

const rules = {
  dictType: [{ required: true, message: '请输入字典类型', trigger: 'blur' }],
  dictCode: [{ required: true, message: '请输入字典编码', trigger: 'blur' }],
  dictName: [{ required: true, message: '请输入显示标签', trigger: 'blur' }]
};

const openCreateDialog = () => {
  isEdit.value = false;
  Object.assign(form, {
    id: null,
    dictType: '',
    dictCode: '',
    dictName: '',
    sortNo: 0,
    version: 0
  });
  dialogVisible.value = true;
  if (formRef.value) formRef.value.clearValidate();
};

const openEditDialog = async (row) => {
  try {
    const res = await getDictionaryDetail(row.dictType, row.dictCode);
    const detail = res?.data || res || {};

    isEdit.value = true;
    Object.assign(form, {
      id: detail.dictId || detail.id,
      dictType: detail.dictType,
      dictCode: detail.dictCode,
      dictName: detail.dictName,
      sortNo: detail.sortNo || 0,
      version: detail.version
    });
    dialogVisible.value = true;
    if (formRef.value) formRef.value.clearValidate();
  } catch (error) {
    ElMessage.error(getApiErrorMessage(error, '获取详情失败'));
  }
};

const submitForm = () => {
  if (!formRef.value) return;
  formRef.value.validate(async (valid) => {
    if (!valid) return;
    
    submitting.value = true;
    try {
      const payload = {
        dictType: form.dictType,
        dictCode: form.dictCode,
        dictName: form.dictName,
        sortNo: form.sortNo,
        version: form.version
      };

      if (isEdit.value) {
        await updateDictionaryItem(form.id, payload);
        ElMessage.success('修改成功');
      } else {
        await createDictionaryItem(payload);
        ElMessage.success('新增成功');
      }
      
      clearAllReferenceCache();
      dialogVisible.value = false;
      fetchList();
    } catch (error) {
      if (isApiConflict(error)) {
        ElMessage.error('数据已被其他用户修改，请刷新后重试');
      } else {
        ElMessage.error(getApiErrorMessage(error, '提交失败'));
      }
    } finally {
      submitting.value = false;
    }
  });
};

const toggleStatus = async (row) => {
  const action = row.status === 'ENABLED' ? '禁用' : '启用';
  try {
    await ElMessageBox.confirm(`确定要${action}该字典项吗？`, '提示', { type: 'warning' });
    
    // Refresh detail to get latest version
    const detailRes = await getDictionaryDetail(row.dictType, row.dictCode);
    const detail = detailRes?.data || detailRes || {};
    const id = detail.dictId || detail.id;
    
    if (row.status === 'ENABLED') {
      await disableDictionaryItem(id, detail.version);
    } else {
      await enableDictionaryItem(id, detail.version);
    }
    
    ElMessage.success(`${action}成功`);
    clearAllReferenceCache();
    fetchList();
  } catch (error) {
    if (error !== 'cancel') {
      if (isApiConflict(error)) {
        ElMessage.error('状态已变化，请刷新列表');
      } else {
        ElMessage.error(getApiErrorMessage(error, `${action}失败`));
      }
    }
  }
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
