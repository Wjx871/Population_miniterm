<template>
  <div class="page-container">
    <div class="page-header"><div><h1>户籍历史归档</h1><p class="subtitle">归档为只读历史快照，不提供编辑、恢复或删除入口。</p></div></div>
    <SearchPanel @search="fetchList" @reset="reset">
      <el-form :inline="true" :model="query" size="default">
        <el-form-item label="人员姓名">
          <el-input v-model.trim="query.personName" placeholder="请输入姓名" clearable />
        </el-form-item>
        <el-form-item label="身份证号">
          <el-input v-model.trim="query.identityNo" placeholder="请输入身份证号" clearable />
        </el-form-item>
        <el-form-item label="户号">
          <el-input v-model.trim="query.householdNo" placeholder="请输入户号" clearable />
        </el-form-item>
        <el-form-item label="归档类型">
          <el-input v-model.trim="query.archiveType" placeholder="请输入归档类型" clearable />
        </el-form-item>
      </el-form>
    </SearchPanel>
    <el-card shadow="never"><el-table :data="records" v-loading="loading" border stripe><el-table-column prop="archiveId" label="归档 ID" width="100"/><el-table-column prop="personName" label="人员姓名" width="120"/><el-table-column label="身份证号" width="180"><template #default="{row}"><SensitiveText :value="row.identityNo" kind="idCard" /></template></el-table-column><el-table-column prop="householdNo" label="户号" width="150"/><el-table-column prop="archiveType" label="归档类型" width="150"/><el-table-column prop="archiveReason" label="归档原因" min-width="160" show-overflow-tooltip/><el-table-column label="归档时间" min-width="170"><template #default="{row}">{{ formatDateTime(row.archivedAt) }}</template></el-table-column><el-table-column label="操作" width="90" fixed="right"><template #default="{row}"><el-button link type="primary" @click="open(row)">查看</el-button></template></el-table-column></el-table><AppPagination v-model:current="query.current" v-model:size="query.size" :total="total" @change="fetchList" /></el-card>
    <DetailDrawer v-model="drawerVisible" title="归档详情" :loading="detailLoading"><el-descriptions v-if="detail" :column="1" border><el-descriptions-item label="归档 ID">{{ detail.archiveId }}</el-descriptions-item><el-descriptions-item label="关联申请">{{ detail.applicationId || '-' }}</el-descriptions-item><el-descriptions-item label="人员姓名">{{ detail.personName }}</el-descriptions-item><el-descriptions-item label="身份证号"><SensitiveText :value="detail.identityNo" kind="idCard" /></el-descriptions-item><el-descriptions-item label="登记地址">{{ detail.registeredAddress || '-' }}</el-descriptions-item><el-descriptions-item label="归档原因">{{ detail.archiveReason || '-' }}</el-descriptions-item><el-descriptions-item label="归档时间">{{ formatDateTime(detail.archivedAt) }}</el-descriptions-item></el-descriptions></DetailDrawer>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'; import { ElMessage } from 'element-plus'; import SearchPanel from '../../components/common/SearchPanel.vue'; import AppPagination from '../../components/common/AppPagination.vue'; import DetailDrawer from '../../components/common/DetailDrawer.vue'; import SensitiveText from '../../components/common/SensitiveText.vue'; import { getResidenceArchiveDetail, getResidenceArchivePage } from '../../api/migrations'; import { normalizePageResult } from '../../utils/page'; import { formatDateTime } from '../../utils/date'; import { getApiErrorMessage } from '../../utils/apiError'
const loading=ref(false);const records=ref([]);const total=ref(0);const query=reactive({personName:'',identityNo:'',householdNo:'',archiveType:'',current:1,size:10});const drawerVisible=ref(false);const detailLoading=ref(false);const detail=ref(null);async function fetchList(){loading.value=true;try{const page=normalizePageResult(await getResidenceArchivePage(query));records.value=page.records;total.value=page.total}finally{loading.value=false}}function reset(){Object.assign(query,{personName:'',identityNo:'',householdNo:'',archiveType:'',current:1});fetchList()}async function open(row){drawerVisible.value=true;detailLoading.value=true;try{detail.value=await getResidenceArchiveDetail(row.archiveId)}catch(error){ElMessage.error(getApiErrorMessage(error,'加载归档详情失败'))}finally{detailLoading.value=false}}onMounted(fetchList)
</script>

<style scoped>.page-container{display:flex;flex-direction:column;gap:16px}.page-header h1{margin:0 0 8px}.subtitle{margin:0;color:var(--el-text-color-secondary)}</style>
