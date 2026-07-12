<template>
  <div class="page-container">
    <div class="page-header">
      <div class="header-left">
        <h1>系统用户管理</h1>
        <p class="subtitle">管理系统登录账号及角色分配。</p>
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
      </el-table>

      <AppPagination
        v-model:current="query.current"
        v-model:size="query.size"
        :total="total"
        @change="fetchList"
      />
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue';
import SearchPanel from '../../components/common/SearchPanel.vue';
import AppPagination from '../../components/common/AppPagination.vue';
import StatusTag from '../../components/common/StatusTag.vue';
import { getUserPage } from '../../api/users';
import { normalizePageResult } from '../../utils/page';

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
  query.username = '';
  query.realName = '';
  query.current = 1;
  fetchList();
};

onMounted(() => {
  fetchList();
});

// 用户管理暂时只读：待后端提供角色列表和角色分配契约后恢复编辑能力。
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
