<template>
  <div class="page-container">
    <div class="page-header">
      <div class="header-left">
        <h1>通用证件管理</h1>
        <p class="subtitle">管理通用证件记录（居民身份证等）。居住证请通过专业居住证管理页面操作。</p>
      </div>
      <div class="header-right">
        <el-button type="primary" :icon="Plus" @click="openCreateDialog" v-permission="'certificate:edit'">颁发/登记证件</el-button>
      </div>
    </div>

    <SearchPanel @search="fetchList" @reset="resetQuery">
      <el-form :inline="true" :model="query" size="default">
        <el-form-item label="证件类型">
          <el-select v-model="query.certificateType" placeholder="请选择证件类型" clearable style="width: 150px;">
            <el-option v-for="t in dictTypes" :key="t.value" :label="t.label" :value="t.value" />
            <el-option v-if="dictTypes.length === 0" label="护照" value="PASSPORT" />
            <el-option v-if="dictTypes.length === 0" label="机动车驾驶证" value="DRIVER_LICENSE" />
            <el-option v-if="dictTypes.length === 0" label="其他证件" value="OTHER" />
          </el-select>
        </el-form-item>
        <el-form-item label="证件状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 120px;">
            <el-option label="有效" value="ACTIVE" />
            <el-option label="已过期" value="EXPIRED" />
            <el-option label="已注销" value="CANCELLED" />
          </el-select>
        </el-form-item>
      </el-form>
    </SearchPanel>

    <el-card shadow="never" class="table-card">
      <el-table :data="tableData" v-loading="loading" border stripe style="width: 100%">
        <el-table-column prop="personName" label="持有者姓名" width="120" align="center" fixed />
        <el-table-column prop="certificateType" label="证件类型" width="130" align="center">
          <template #default="{ row }">
            <template v-if="isLegacyResidenceType(row.certificateType)">
              {{ row.certificateType }} <el-tag size="small" type="info">历史兼容</el-tag>
            </template>
            <template v-else>{{ row.certificateType }}</template>
          </template>
        </el-table-column>
        <el-table-column prop="certificateNo" label="证件编号" width="200" align="center">
          <template #default="{ row }">
            <SensitiveText :value="row.certificateNo" kind="text" />
          </template>
        </el-table-column>
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
            <template v-if="isLegacyResidenceType(row.certificateType)">
              <span class="legacy-readonly">仅可查看</span>
            </template>
            <template v-else>
              <el-button 
                size="small" 
                type="primary" 
                link 
                @click="openEditDialog(row)" 
                v-permission="'certificate:edit'"
                v-if="row.status !== 'CANCELLED'"
              >
                编辑
              </el-button>
              <el-button 
                size="small" 
                type="danger" 
                link 
                @click="handleCancel(row)" 
                v-permission="'certificate:edit'"
                v-if="row.status !== 'CANCELLED'"
              >
                注销
              </el-button>
            </template>
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
          <el-select v-model="form.certificateType" style="width: 100%;" :disabled="dictTypes.length === 0">
            <el-option v-for="t in dictTypes" :key="t.value" :label="t.label" :value="t.value" />
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
import SensitiveText from '../../components/common/SensitiveText.vue';
import PersonSelect from '../../components/business/PersonSelect.vue';
import { getCertificatePage, getCertificateById, createCertificate, updateCertificate, cancelCertificate } from '../../api/certificates';
import { getDictionaryItems } from '../../api/dictionaries';
import { normalizeDictionaryList } from '../../adapters/dictionary';
import { containsMaskedValue, toCertificateCreatePayload, toCertificateUpdatePayload, toCertificateCancelPayload } from '../../adapters/certificate';
import { formatDate } from '../../utils/date';
import { normalizePageResult } from '../../utils/page';
import { getApiErrorMessage, isApiConflict } from '../../utils/apiError';

const loading = ref(false);
const tableData = ref([]);
const total = ref(0);
const dictTypes = ref([]);

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
    ElMessage.error(getApiErrorMessage(error, '加载列表失败'));
  } finally {
    loading.value = false;
  }
};

const loadDict = async () => {
  try {
    const res = await getDictionaryItems('CERTIFICATE_TYPE');
    const items = res?.data || res || [];
    dictTypes.value = normalizeDictionaryList(items);
  } catch (e) {
    dictTypes.value = [];
  }
};

const resetQuery = () => {
  query.certificateType = '';
  query.status = '';
  query.current = 1;
  fetchList();
};

onMounted(() => {
  loadDict();
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
  certificateType: '',
  certificateNo: '',
  issueDate: '',
  expireDate: '',
  version: 0
});

const rules = {
  personId: [{ required: true, message: '请选择持有人', trigger: 'change' }],
  certificateType: [{ required: true, message: '请选择证件类型', trigger: 'change' }],
  certificateNo: [{ required: true, message: '请输入证件编号', trigger: 'blur' }],
  issueDate: [{ required: true, message: '请选择签发日期', trigger: 'change' }]
};

const openCreateDialog = () => {
  isEdit.value = false;
  Object.assign(form, {
    id: null,
    personId: null,
    personName: '',
    certificateType: '',
    certificateNo: '',
    issueDate: '',
    expireDate: '',
    version: 0
  });
  dialogVisible.value = true;
  if (formRef.value) formRef.value.clearValidate();
};

const openEditDialog = async (row) => {
  const id = row.id || row.certificateId;
  try {
    const res = await getCertificateById(id);
    const detail = res?.data || res || {};
    
    if (containsMaskedValue(detail.certificateNo)) {
      ElMessage.error('当前账号无权查看完整证件号，无法安全修改该证件');
      return;
    }
    
    if (detail.status === 'CANCELLED') {
      ElMessage.warning('已注销证件不允许编辑');
      return;
    }

    isEdit.value = true;
    Object.assign(form, {
      id: detail.id || detail.certificateId,
      personId: detail.personId,
      personName: detail.personName || row.personName,
      certificateType: detail.certificateType,
      certificateNo: detail.certificateNo,
      issueDate: formatDate(detail.issueDate),
      expireDate: formatDate(detail.expireDate),
      version: detail.version
    });
    dialogVisible.value = true;
    if (formRef.value) formRef.value.clearValidate();
  } catch (error) {
    ElMessage.error(getApiErrorMessage(error, '获取证件详情失败'));
  }
};

const submitForm = () => {
  if (!formRef.value) return;
  formRef.value.validate(async (valid) => {
    if (!valid) return;
    
    submitting.value = true;
    try {
      if (isEdit.value) {
        const payload = toCertificateUpdatePayload(form, form.version);
        await updateCertificate(form.id, payload);
        ElMessage.success('修改成功');
      } else {
        const payload = toCertificateCreatePayload(form);
        await createCertificate(payload);
        ElMessage.success('登记成功');
      }
      dialogVisible.value = false;
      fetchList();
    } catch (error) {
      if (error?.message === '禁止提交脱敏证件号') {
        ElMessage.error(error.message);
      } else if (isApiConflict(error)) {
        ElMessage.error('数据已被其他用户修改，请刷新后重试');
      } else {
        ElMessage.error(getApiErrorMessage(error, '提交失败'));
      }
    } finally {
      submitting.value = false;
    }
  });
};

function isLegacyResidenceType(type) {
  return ['居住证', '临时居住证'].includes(type)
}

const handleCancel = async (row) => {
  if (isLegacyResidenceType(row.certificateType)) return
  const id = row.id || row.certificateId;
  
  try {
    const res = await getCertificateById(id);
    const detail = res?.data || res || {};
    
    ElMessageBox.prompt('请输入注销原因', '注销证件', {
      confirmButtonText: '确定注销',
      cancelButtonText: '取消',
      inputValidator: (value) => {
        if (!value || value.trim() === '') {
          return '注销原因不能为空';
        }
        return true;
      },
      inputErrorMessage: '注销原因不能为空'
    }).then(async ({ value }) => {
      try {
        const payload = toCertificateCancelPayload(value, detail.version);
        await cancelCertificate(id, payload);
        ElMessage.success('注销成功');
        fetchList();
      } catch (error) {
        if (isApiConflict(error)) {
          ElMessage.error('状态已变化，请刷新列表');
        } else {
          ElMessage.error(getApiErrorMessage(error, '注销失败'));
        }
      }
    }).catch(() => {});
  } catch (error) {
    ElMessage.error(getApiErrorMessage(error, '获取证件详情失败'));
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
.legacy-readonly {
  color: var(--el-text-color-placeholder);
  font-size: 13px;
}
</style>
