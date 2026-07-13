<template>
  <div class="query-page">
    <div class="page-header"><div><h1>迁移历史查询</h1><p>统一查询已执行的迁入、迁出历史。</p></div><el-button :icon="Refresh" @click="load">刷新</el-button></div>
    <SearchPanel @search="search" @reset="reset"><el-form :inline="true" :model="query">
      <el-form-item label="人员姓名"><el-input v-model.trim="query.personName" clearable/></el-form-item>
      <el-form-item label="申请编号"><el-input v-model.trim="query.applicationNo" clearable/></el-form-item>
      <el-form-item label="方向"><el-select v-model="query.migrationType" clearable style="width:140px"><el-option label="迁入" value="IN"/><el-option label="迁出" value="OUT"/></el-select></el-form-item>
      <el-form-item label="状态"><el-input v-model.trim="query.status" clearable placeholder="如 COMPLETED"/></el-form-item>
      <el-form-item label="执行日期"><el-date-picker v-model="dateRange" type="daterange" value-format="YYYY-MM-DD" range-separator="至" start-placeholder="开始" end-placeholder="结束"/></el-form-item>
    </el-form></SearchPanel>
    <el-alert v-if="error" title="迁移历史查询失败" type="error" show-icon :closable="false"><el-button link type="primary" @click="load">重试</el-button></el-alert>
    <el-card shadow="never"><el-table v-loading="loading" :data="records" empty-text="查询成功，暂无匹配数据">
      <el-table-column prop="applicationNo" label="申请编号" min-width="180"/><el-table-column prop="personName" label="人员" width="110"/><el-table-column prop="direction" label="方向" width="90"/>
      <el-table-column prop="migrationType" label="迁移类型" min-width="120"/><el-table-column prop="sourceRegionCode" label="来源区划" min-width="110"/><el-table-column prop="targetRegionCode" label="目标区划" min-width="110"/>
      <el-table-column prop="executeDate" label="执行日期" width="120"/><el-table-column prop="status" label="状态" min-width="110"/>
    </el-table><AppPagination v-model:current="query.current" v-model:size="query.size" :total="total" @change="load"/></el-card>
  </div>
</template>
<script setup>
import { onMounted, reactive, ref } from 'vue'; import { Refresh } from '@element-plus/icons-vue'
import SearchPanel from '../../components/common/SearchPanel.vue'; import AppPagination from '../../components/common/AppPagination.vue'
import { queryMigrationHistory } from '../../api/query.js'; import { normalizeMigrationQueryPage } from '../../adapters/query.js'
const initial=()=>({personName:'',applicationNo:'',migrationType:'',status:'',current:1,size:10}); const query=reactive(initial()); const dateRange=ref([]); const records=ref([]);const total=ref(0);const loading=ref(false);const error=ref(false)
async function load(){loading.value=true;try{const params={...query,executeDateFrom:dateRange.value?.[0],executeDateTo:dateRange.value?.[1]};const page=normalizeMigrationQueryPage(await queryMigrationHistory(params));records.value=page.records;total.value=page.total;error.value=false}catch{error.value=true}finally{loading.value=false}}
function search(){query.current=1;load()} function reset(){Object.assign(query,initial());dateRange.value=[];load()} onMounted(load)
</script>
<style scoped>.query-page{display:flex;flex-direction:column;gap:16px}.page-header{display:flex;justify-content:space-between;align-items:flex-end}.page-header h1{margin:0 0 6px;font-size:24px}.page-header p{color:var(--color-ink-muted)}</style>
