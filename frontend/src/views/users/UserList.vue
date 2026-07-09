<template>
  <div class="page-container">
    <div class="page-header">
      <div class="header-left">
        <h1>系统用户管理</h1>
        <p class="subtitle">管理系统登录账号及角色分配。</p>
      </div>
      <div class="header-right">
        <el-button type="primary" :icon="Plus" @click="openCreateDialog">新增用户</el-button>
      </div>
    </div>

    <SearchPanel @search="fetchList" @reset="resetQuery">
      <el-form :inline="true" :model="query" size="default">
        <el-form-item label="用户名">
          <el-input v-model="query.username" placeholder="请输入用户名" clearable />
        </el-form-item>
        <el-form-item label="真实姓名">
          <el-input v-model="query.realName" placeholder="请输入真实姓名" clearable />
        </el-form-item>
      </el-form>
    </SearchPanel>

    <el-card shadow="never" class="table-card">
      <el-table :data="tableData" v-loading="loading" border stripe style="width: 100%">
        <el-table-column prop="username" label="用户名" width="150" align="center" fixed />
        <el-table-column prop="realName" label="真实姓名" width="150" align="center" />
        <el-table-column prop="roleName" label="系统角色" width="150" align="center">
          <template #default="{ row }">
            <el-tag :type="row.roleName === '系统管理员' || row.roleName === 'admin' ? 'danger' : 'primary'" size="small">
              {{ row.roleName }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100" align="center">
          <template #default="{ row }">
            <StatusTag :value="row.status || '正常'" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" align="center" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" link @click="openEditDialog(row)">编辑</el-button>
            <el-button 
              size="small" 
              type="danger" 
              link 
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
      :title="isEdit ? '编辑用户' : '新增用户'"
      :loading="submitting"
      @confirm="submitForm"
    >
      <el-form 
        ref="formRef" 
        :model="form" 
        :rules="rules" 
        label-width="100px" 
      >
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" placeholder="请输入登录账号" :disabled="isEdit" />
        </el-form-item>
        <el-form-item label="初始密码" prop="password" v-if="!isEdit">
          <el-input v-model="form.password" type="password" placeholder="请输入初始密码" show-password />
        </el-form-item>
        <el-form-item label="真实姓名" prop="realName">
          <el-input v-model="form.realName" placeholder="请输入真实姓名" />
        </el-form-item>
        <el-form-item label="角色分配" prop="roleId">
          <el-select v-model="form.roleId" style="width: 100%;">
            <!-- 简化处理：固定两个角色，后端根据 roleId 关联 -->
            <el-option label="系统管理员" :value="1" />
            <el-option label="普通操作员" :value="2" />
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
import { getUserPage, createUser, updateUser, deleteUser } from '../../api/users';

const loading = ref(false);
const tableData = ref([]);
const total = ref(0);

const query = reactive({
  username: '',
  realName: '',
  current: 1,
  size: 10
});

const fetchList = async () => {
  loading.value = true;
  try {
    const res = await getUserPage(query);
    tableData.value = res.records || res.content || [];
    total.value = res.total || res.totalElements || 0;
  } catch (error) {
    console.error(error);
  } finally {
    loading.value = false;
  }
};

const resetQuery = () => {
  query.username = '';
  query.realName = '';
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
  username: '',
  password: '',
  realName: '',
  roleId: 2
});

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入初始密码', trigger: 'blur' }],
  realName: [{ required: true, message: '请输入真实姓名', trigger: 'blur' }],
  roleId: [{ required: true, message: '请选择角色', trigger: 'change' }]
};

const openCreateDialog = () => {
  isEdit.value = false;
  Object.assign(form, {
    id: null,
    username: '',
    password: '',
    realName: '',
    roleId: 2
  });
  dialogVisible.value = true;
  if (formRef.value) formRef.value.clearValidate();
};

const openEditDialog = (row) => {
  isEdit.value = true;
  Object.assign(form, {
    id: row.id || row.userId,
    username: row.username,
    password: '',
    realName: row.realName,
    roleId: row.roleId || 2
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
        // 不需要传密码，密码修改通过重置密码实现，或者后端忽略
        delete payload.password;
        await updateUser(form.id, payload);
        ElMessage.success('修改成功');
      } else {
        await createUser(payload);
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
  const id = row.id || row.userId;
  ElMessageBox.confirm(`确定要删除用户 [${row.username}] 吗？`, '警告', {
    confirmButtonText: '确定删除',
    cancelButtonText: '取消',
    type: 'warning',
  }).then(async () => {
    try {
      await deleteUser(id);
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
