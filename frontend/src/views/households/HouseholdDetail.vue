<template>
  <div class="page-container">
    <div class="page-header">
      <div class="header-left">
        <el-button link :icon="ArrowLeft" @click="router.back()" style="margin-bottom: 8px;">返回列表</el-button>
        <h1>户籍详情：{{ householdInfo.householdNo || '加载中...' }}</h1>
        <p class="subtitle">家庭住址：{{ householdInfo.address || '加载中...' }} | 户主：{{ householdInfo.headPersonName || '...' }}</p>
      </div>
      <div class="header-right">
        <el-button type="primary" :icon="Plus" @click="openAddDialog">添加成员</el-button>
      </div>
    </div>

    <el-card shadow="never" class="table-card">
      <el-table :data="members" v-loading="loading" border stripe style="width: 100%">
        <el-table-column prop="personName" label="姓名" width="120" />
        <el-table-column prop="relationship" label="与户主关系" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="row.relationship === '户主' ? 'primary' : 'info'" size="small">
              {{ row.relationship }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="idCard" label="身份证号" width="180" align="center" />
        <el-table-column prop="joinDate" label="加入时间" width="130" align="center">
          <template #default="{ row }">{{ formatDate(row.joinDate) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="150" align="center" fixed="right">
          <template #default="{ row }">
            <el-button 
              size="small" 
              type="danger" 
              link 
              :disabled="row.relationship === '户主'"
              @click="handleRemove(row)"
            >
              移除
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
          <PersonSelect v-model="form.personId" />
        </el-form-item>
        <el-form-item label="与户主关系" prop="relationship">
          <el-select v-model="form.relationship" style="width: 100%">
            <el-option label="配偶" value="配偶" />
            <el-option label="子" value="子" />
            <el-option label="女" value="女" />
            <el-option label="父母" value="父母" />
            <el-option label="其他亲属" value="其他亲属" />
            <el-option label="非亲属" value="非亲属" />
          </el-select>
        </el-form-item>
        <el-form-item label="加入日期" prop="joinDate">
          <el-date-picker 
            v-model="form.joinDate" 
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
import { useRoute, useRouter } from 'vue-router';
import { ArrowLeft, Plus } from '@element-plus/icons-vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import FormDialog from '../../components/common/FormDialog.vue';
import PersonSelect from '../../components/business/PersonSelect.vue';
import { getHouseholdById, getHouseholdMembers, addHouseholdMember, removeHouseholdMember } from '../../api/households';
import { formatDate } from '../../utils/date';
import dayjs from 'dayjs';

const route = useRoute();
const router = useRouter();
const householdId = route.params.id;

const loading = ref(false);
const householdInfo = ref({});
const members = ref([]);

const fetchDetail = async () => {
  loading.value = true;
  try {
    const [infoRes, membersRes] = await Promise.all([
      getHouseholdById(householdId),
      getHouseholdMembers(householdId)
    ]);
    // Compatible with spring backend return format
    householdInfo.value = infoRes.data || infoRes; 
    members.value = membersRes.data || membersRes || [];
  } catch (error) {
    console.error(error);
  } finally {
    loading.value = false;
  }
};

onMounted(() => {
  fetchDetail();
});

// Dialog
const dialogVisible = ref(false);
const submitting = ref(false);
const formRef = ref(null);

const form = reactive({
  personId: null,
  relationship: '',
  joinDate: dayjs().format('YYYY-MM-DD')
});

const rules = {
  personId: [{ required: true, message: '请选择人员', trigger: 'change' }],
  relationship: [{ required: true, message: '请选择与户主关系', trigger: 'change' }],
  joinDate: [{ required: true, message: '请选择加入日期', trigger: 'change' }]
};

const openAddDialog = () => {
  Object.assign(form, {
    personId: null,
    relationship: '',
    joinDate: dayjs().format('YYYY-MM-DD')
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
      await addHouseholdMember(householdId, { ...form });
      ElMessage.success('添加成员成功');
      dialogVisible.value = false;
      fetchDetail();
    } catch (error) {
      console.error(error);
    } finally {
      submitting.value = false;
    }
  });
};

const handleRemove = (row) => {
  const memberId = row.id || row.memberId || row.personId;
  ElMessageBox.confirm(`确定要移除成员 [${row.personName}] 吗？`, '警告', {
    confirmButtonText: '确定移除',
    cancelButtonText: '取消',
    type: 'warning',
  }).then(async () => {
    try {
      await removeHouseholdMember(householdId, memberId);
      ElMessage.success('移除成功');
      fetchDetail();
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
