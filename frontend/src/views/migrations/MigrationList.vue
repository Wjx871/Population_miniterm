<template>
  <div class="page-container">
    <div class="page-header">
      <div class="header-left">
        <h1>{{ isMigrationIn ? '迁入登记' : '迁出注销' }}管理</h1>
        <p class="subtitle">记录人员{{ isMigrationIn ? '迁入' : '迁出' }}信息，实时同步人员状态变更。</p>
      </div>
      <div class="header-right">
        <el-button :type="isMigrationIn ? 'primary' : 'danger'" :icon="Plus" @click="openCreateDialog">
          办理{{ isMigrationIn ? '迁入' : '迁出' }}
        </el-button>
      </div>
    </div>

    <SearchPanel @search="fetchList" @reset="resetQuery">
      <el-form :inline="true" :model="query" size="default">
        <el-form-item label="人员姓名">
          <el-input v-model="query.name" placeholder="请输入姓名" clearable />
        </el-form-item>
        <el-form-item label="身份证号">
          <el-input v-model="query.idCard" placeholder="请输入身份证号" clearable />
        </el-form-item>
      </el-form>
    </SearchPanel>

    <el-card shadow="never" class="table-card">
      <el-table :data="tableData" v-loading="loading" border stripe style="width: 100%">
        <el-table-column prop="personName" label="人员姓名" width="120" align="center" fixed />
        <el-table-column prop="personIdCard" label="身份证号" width="180" align="center" />
        <el-table-column prop="householdNo" label="户号" width="180" align="center" />
        <el-table-column prop="operatorName" label="经办人" width="120" align="center" />
        
        <el-table-column v-if="isMigrationIn" prop="inDate" label="变更日期" width="120" align="center">
          <template #default="{ row }">{{ formatDate(row.inDate) }}</template>
        </el-table-column>
        <el-table-column v-else prop="outDate" label="变更日期" width="120" align="center">
          <template #default="{ row }">{{ formatDate(row.outDate) }}</template>
        </el-table-column>
        
        <el-table-column v-if="isMigrationIn" prop="fromAddress" label="来源住址" min-width="200" show-overflow-tooltip />
        <el-table-column v-else prop="toAddress" label="迁往住址" min-width="200" show-overflow-tooltip />
        
        <el-table-column prop="reason" label="变更原因" min-width="150" show-overflow-tooltip />
        
        <el-table-column label="操作" width="100" align="center" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="danger" link @click="handleDelete(row)">撤销</el-button>
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
      :title="isMigrationIn ? '办理户口迁入登记' : '办理户口迁出注销'"
      :loading="submitting"
      @confirm="submitForm"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-form-item label="办理人员" prop="personId">
          <PersonSelect v-model="form.personId" />
        </el-form-item>
        
        <template v-if="isMigrationIn">
          <el-form-item label="迁入日期" prop="inDate">
            <el-date-picker v-model="form.inDate" type="date" placeholder="请选择日期" value-format="YYYY-MM-DD" style="width: 100%;" />
          </el-form-item>
          <el-form-item label="来源住址" prop="fromAddress">
            <el-input v-model="form.fromAddress" placeholder="省、市、区及详细住址" />
          </el-form-item>
          <el-form-item label="迁入户籍ID" prop="toHouseholdId">
            <el-input v-model="form.toHouseholdId" placeholder="请输入迁入的户籍ID（选填）" />
          </el-form-item>
        </template>
        
        <template v-else>
          <el-form-item label="迁出日期" prop="outDate">
            <el-date-picker v-model="form.outDate" type="date" placeholder="请选择日期" value-format="YYYY-MM-DD" style="width: 100%;" />
          </el-form-item>
          <el-form-item label="原户籍ID" prop="fromHouseholdId">
            <el-input v-model="form.fromHouseholdId" placeholder="请输入原户籍ID（选填）" />
          </el-form-item>
          <el-form-item label="迁往住址" prop="toAddress">
            <el-input v-model="form.toAddress" placeholder="省、市、区及详细住址" />
          </el-form-item>
        </template>

        <el-form-item label="变更原因" prop="reason">
          <el-input v-model="form.reason" type="textarea" :rows="2" placeholder="请简要描述变更原因" />
        </el-form-item>
      </el-form>
    </FormDialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed, watch } from 'vue';
import { useRoute } from 'vue-router';
import { Plus } from '@element-plus/icons-vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import SearchPanel from '../../components/common/SearchPanel.vue';
import AppPagination from '../../components/common/AppPagination.vue';
import FormDialog from '../../components/common/FormDialog.vue';
import PersonSelect from '../../components/business/PersonSelect.vue';
import { 
  getMigrationInPage, createMigrationIn, deleteMigrationIn,
  getMigrationOutPage, createMigrationOut, deleteMigrationOut
} from '../../api/migrations';
import { formatDate } from '../../utils/date';

const route = useRoute();

const isMigrationIn = computed(() => route.meta.type === 'in');

const loading = ref(false);
const tableData = ref([]);
const total = ref(0);

const query = reactive({
  name: '',
  idCard: '',
  current: 1,
  size: 10
});

watch(isMigrationIn, () => {
  resetQuery();
});

const fetchList = async () => {
  loading.value = true;
  try {
    const apiCall = isMigrationIn.value ? getMigrationInPage : getMigrationOutPage;
    const res = await apiCall(query);
    tableData.value = res?.records || res?.content || [];
    total.value = res?.total || res?.totalElements || 0;
  } catch (error) {
    console.error(error);
  } finally {
    loading.value = false;
  }
};

const resetQuery = () => {
  query.name = '';
  query.idCard = '';
  query.current = 1;
  fetchList();
};

onMounted(() => {
  fetchList();
});

// Dialog
const dialogVisible = ref(false);
const submitting = ref(false);
const formRef = ref(null);

const form = reactive({
  personId: null,
  inDate: '',
  outDate: '',
  fromAddress: '',
  toAddress: '',
  toHouseholdId: '',
  fromHouseholdId: '',
  reason: ''
});

const rules = computed(() => {
  if (isMigrationIn.value) {
    return {
      personId: [{ required: true, message: '请选择办理人员', trigger: 'change' }],
      inDate: [{ required: true, message: '请选择迁入日期', trigger: 'change' }],
      fromAddress: [{ required: true, message: '请输入来源住址', trigger: 'blur' }]
    };
  } else {
    return {
      personId: [{ required: true, message: '请选择办理人员', trigger: 'change' }],
      outDate: [{ required: true, message: '请选择迁出日期', trigger: 'change' }],
      toAddress: [{ required: true, message: '请输入迁往住址', trigger: 'blur' }]
    };
  }
});

const openCreateDialog = () => {
  Object.assign(form, {
    personId: null,
    inDate: '',
    outDate: '',
    fromAddress: '',
    toAddress: '',
    toHouseholdId: '',
    fromHouseholdId: '',
    reason: ''
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
      if (isMigrationIn.value) {
        await createMigrationIn({
          personId: form.personId,
          inDate: form.inDate,
          fromAddress: form.fromAddress,
          toHouseholdId: form.toHouseholdId,
          reason: form.reason
        });
      } else {
        await createMigrationOut({
          personId: form.personId,
          outDate: form.outDate,
          fromHouseholdId: form.fromHouseholdId,
          toAddress: form.toAddress,
          reason: form.reason
        });
      }
      ElMessage.success('办理成功');
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
  const id = isMigrationIn.value ? row.inId : row.outId;
  ElMessageBox.confirm(`确定要撤销此条记录吗？这可能不会自动恢复人员状态。`, '警告', {
    confirmButtonText: '确定撤销',
    cancelButtonText: '取消',
    type: 'warning',
  }).then(async () => {
    try {
      if (isMigrationIn.value) {
        await deleteMigrationIn(id);
      } else {
        await deleteMigrationOut(id);
      }
      ElMessage.success('撤销成功');
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
