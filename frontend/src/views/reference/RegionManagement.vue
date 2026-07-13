<template>
  <div class="page-container">
    <div class="page-header">
      <div class="header-left">
        <h1>行政区划管理</h1>
        <p class="subtitle">管理系统的行政区划层级。禁用的区划不会在地址选择器中出现。</p>
      </div>
      <div class="header-right">
        <el-button type="primary" :icon="Plus" @click="openCreateDialog">新增区划</el-button>
      </div>
    </div>

    <el-card shadow="never" class="table-card">
      <el-table 
        :data="tableData" 
        v-loading="loading" 
        row-key="regionCode"
        :tree-props="{ children: 'children', hasChildren: 'hasChildren' }"
        border 
        style="width: 100%"
      >
        <el-table-column prop="regionName" label="区划名称" min-width="200" />
        <el-table-column prop="regionCode" label="区划代码" width="180" />
        <el-table-column prop="parentCode" label="上级代码" width="180" />
        <el-table-column prop="businessStatus" label="状态" width="100" align="center">
          <template #default="{ row }">
            <StatusTag :value="row.businessStatus" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" align="center" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" link @click="openEditDialog(row)">编辑</el-button>
            <el-button 
              size="small" 
              :type="row.businessStatus === 'ACTIVE' ? 'danger' : 'success'" 
              link 
              @click="toggleStatus(row)"
            >
              {{ row.businessStatus === 'ACTIVE' ? '禁用' : '启用' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <FormDialog 
      v-model:visible="dialogVisible" 
      :title="isEdit ? '编辑行政区划' : '新增行政区划'"
      :loading="submitting"
      @confirm="submitForm"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="区划代码" prop="regionCode">
          <el-input v-model="form.regionCode" :disabled="isEdit" placeholder="唯一区划代码" />
        </el-form-item>
        <el-form-item label="区划名称" prop="regionName">
          <el-input v-model="form.regionName" placeholder="区划名称" />
        </el-form-item>
        <el-form-item label="上级代码" prop="parentCode">
          <el-input v-model="form.parentCode" placeholder="顶层可为空" />
        </el-form-item>
      </el-form>
    </FormDialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue';
import { Plus } from '@element-plus/icons-vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import FormDialog from '../../components/common/FormDialog.vue';
import StatusTag from '../../components/common/StatusTag.vue';
import { getRegionTree, getRegionDetail, createRegion, updateRegion, enableRegion, disableRegion } from '../../api/regions';
import { normalizeRegionTree } from '../../adapters/region';
import { clearAllReferenceCache } from '../../services/referenceDataCache';
import { getApiErrorMessage, isApiConflict } from '../../utils/apiError';

const loading = ref(false);
const tableData = ref([]);

const fetchList = async () => {
  loading.value = true;
  try {
    const res = await getRegionTree();
    const tree = res?.data || res || [];
    tableData.value = normalizeRegionTree(tree, true); // keep all, including inactive
  } catch (error) {
    ElMessage.error(getApiErrorMessage(error, '加载行政区划失败'));
  } finally {
    loading.value = false;
  }
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
  regionCode: '',
  regionName: '',
  parentCode: '',
  version: 0
});

const rules = {
  regionCode: [{ required: true, message: '请输入区划代码', trigger: 'blur' }],
  regionName: [{ required: true, message: '请输入区划名称', trigger: 'blur' }]
};

const openCreateDialog = () => {
  isEdit.value = false;
  Object.assign(form, {
    regionCode: '',
    regionName: '',
    parentCode: '',
    version: 0
  });
  dialogVisible.value = true;
  if (formRef.value) formRef.value.clearValidate();
};

const openEditDialog = async (row) => {
  try {
    const res = await getRegionDetail(row.regionCode);
    const detail = res?.data || res || {};

    isEdit.value = true;
    Object.assign(form, {
      regionCode: detail.regionCode,
      regionName: detail.regionName,
      parentCode: detail.parentCode || '',
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
        regionCode: form.regionCode,
        regionName: form.regionName,
        parentCode: form.parentCode || null,
        version: form.version
      };

      if (isEdit.value) {
        await updateRegion(form.regionCode, payload);
        ElMessage.success('修改成功');
      } else {
        await createRegion(payload);
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
  const action = row.businessStatus === 'ACTIVE' ? '禁用' : '启用';
  try {
    await ElMessageBox.confirm(`确定要${action}该区划吗？`, '提示', { type: 'warning' });
    
    // Refresh detail to get latest version
    const detailRes = await getRegionDetail(row.regionCode);
    const detail = detailRes?.data || detailRes || {};
    
    if (row.businessStatus === 'ACTIVE') {
      await disableRegion(detail.regionCode, detail.version);
    } else {
      await enableRegion(detail.regionCode, detail.version);
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
