<template>
  <div class="page-container">
    <div class="page-header"><div><h1>我的申请</h1><p class="subtitle">按后端数据权限范围查询业务申请。</p></div></div>
    <SearchPanel @search="fetchList" @reset="reset"><el-form :inline="true"><el-form-item label="申请编号"><el-input v-model.trim="query.applicationNo" clearable /></el-form-item><el-form-item label="业务类型"><el-select v-model="query.businessType" clearable style="width: 160px"><el-option v-for="(label,value) in BUSINESS_TYPE_LABEL" :key="value" :label="label" :value="value" /></el-select></el-form-item><el-form-item label="状态"><el-select v-model="query.status" clearable style="width: 140px"><el-option v-for="(label,value) in APPLICATION_STATUS" :key="value" :label="label" :value="value" /></el-select></el-form-item></el-form></SearchPanel>
    <el-card shadow="never"><el-table :data="records" v-loading="loading" border stripe><el-table-column prop="applicationNo" label="申请编号" width="180"/><el-table-column label="业务类型" width="130"><template #default="{row}">{{ BUSINESS_TYPE_LABEL[row.businessType] || row.businessType }}</template></el-table-column><el-table-column prop="title" label="申请标题" min-width="180" show-overflow-tooltip/><el-table-column prop="applicantName" label="申请人" width="120"/><el-table-column label="状态" width="150"><template #default="{row}"><StatusTag :value="row.status" /></template></el-table-column><el-table-column label="创建时间" min-width="170"><template #default="{row}">{{ formatDateTime(row.createdAt) }}</template></el-table-column><el-table-column label="操作" width="90" fixed="right"><template #default="{row}"><el-button link type="primary" @click="router.push(`/applications/${row.applicationId}`)">查看</el-button></template></el-table-column></el-table><AppPagination v-model:current="query.current" v-model:size="query.size" :total="total" @change="fetchList"/></el-card>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import SearchPanel from '../../components/common/SearchPanel.vue'; import AppPagination from '../../components/common/AppPagination.vue'; import StatusTag from '../../components/common/StatusTag.vue'
import { getApplicationPage } from '../../api/applications'; import { normalizeApplicationPage } from '../../adapters/application'; import { APPLICATION_STATUS, BUSINESS_TYPE_LABEL } from '../../constants/application'; import { formatDateTime } from '../../utils/date'
const router=useRouter();const loading=ref(false);const records=ref([]);const total=ref(0);const query=reactive({applicationNo:'',businessType:'',status:'',current:1,size:10});async function fetchList(){loading.value=true;try{const page=normalizeApplicationPage(await getApplicationPage(query));records.value=page.records;total.value=page.total}finally{loading.value=false}}function reset(){Object.assign(query,{applicationNo:'',businessType:'',status:'',current:1});fetchList()}onMounted(fetchList)
</script>

<style scoped>.page-container{display:flex;flex-direction:column;gap:16px}.page-header h1{margin:0 0 8px}.subtitle{margin:0;color:var(--el-text-color-secondary)}</style>
