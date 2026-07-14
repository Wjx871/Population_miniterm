<template>
  <div class="log-page">
    <div class="page-header"><div><h1>系统日志查询</h1><p>日志详情由后端统一脱敏，并受数据范围约束。</p></div><el-button :icon="Refresh" @click="load">刷新</el-button></div>
    <el-tabs v-model="kind" @tab-change="switchKind"><el-tab-pane label="操作日志" name="operations"/><el-tab-pane label="登录日志" name="logins"/></el-tabs>
    <SearchPanel @search="search" @reset="reset"><el-form :inline="true" :model="query">
      <el-form-item label="用户名"><el-input v-model.trim="query.username" clearable/></el-form-item>
      <el-form-item v-if="kind==='operations'" label="模块"><el-input v-model.trim="query.module" clearable/></el-form-item>
      <el-form-item v-if="kind==='operations'" label="操作类型"><el-input v-model.trim="query.operationType" clearable/></el-form-item>
      <el-form-item label="结果"><el-select v-model="query.result" clearable style="width:130px"><el-option label="成功" value="SUCCESS"/><el-option label="失败" value="FAILURE"/></el-select></el-form-item>
      <el-form-item label="时间"><el-date-picker v-model="query.dateRange" type="datetimerange" value-format="YYYY-MM-DDTHH:mm:ss" range-separator="至" start-placeholder="开始" end-placeholder="结束"/></el-form-item>
    </el-form></SearchPanel>
    <el-alert v-if="error" title="日志加载失败" type="error" show-icon :closable="false"><el-button link type="primary" @click="load">重试</el-button></el-alert>
    <el-card shadow="never"><el-table v-loading="loading" :data="records" empty-text="查询成功，暂无日志">
      <el-table-column prop="operationTime" label="时间" min-width="170"/><el-table-column prop="username" label="用户" width="110"/><el-table-column prop="moduleName" label="模块" min-width="110"/>
      <el-table-column prop="operationType" label="操作" min-width="120"/><el-table-column prop="requestMethod" label="方法" width="80"/><el-table-column prop="requestPath" label="路径" min-width="210" show-overflow-tooltip/>
      <el-table-column prop="ipAddress" label="IP" min-width="130"/><el-table-column prop="operationResult" label="结果" width="90"/>
      <el-table-column label="详情" width="80"><template #default="{row}"><el-button link type="primary" @click="showDetail(row)">查看</el-button></template></el-table-column>
    </el-table><AppPagination v-model:current="query.current" v-model:size="query.size" :total="total" @change="load"/></el-card>
    <el-drawer v-model="detailVisible" title="脱敏日志详情" size="520px"><el-descriptions v-if="detail" :column="1" border><el-descriptions-item label="请求">{{ detail.requestMethod }} {{ detail.requestPath }}</el-descriptions-item><el-descriptions-item label="结果">{{ detail.operationResult || '-' }}</el-descriptions-item><el-descriptions-item label="错误">{{ detail.errorMessage || '-' }}</el-descriptions-item><el-descriptions-item label="详情"><pre>{{ detail.detail || '-' }}</pre></el-descriptions-item></el-descriptions></el-drawer>
  </div>
</template>
<script setup>
import { onMounted, reactive, ref } from 'vue';import { Refresh } from '@element-plus/icons-vue'
import SearchPanel from '../../components/common/SearchPanel.vue';import AppPagination from '../../components/common/AppPagination.vue'
import { getLoginLogs,getOperationLog,getOperationLogs } from '../../api/logs.js';import { normalizeLogPage } from '../../adapters/query.js'
const initial=()=>({username:'',module:'',operationType:'',result:'',dateRange:[],current:1,size:10});const kind=ref('operations');const query=reactive(initial());const records=ref([]);const total=ref(0);const loading=ref(false);const error=ref(false);const detailVisible=ref(false);const detail=ref(null)
async function load(){loading.value=true;try{const page=normalizeLogPage(await (kind.value==='logins'?getLoginLogs(query):getOperationLogs(query)));records.value=page.records;total.value=page.total;error.value=false}catch{error.value=true}finally{loading.value=false}}
function search(){query.current=1;load()}function reset(){Object.assign(query,initial());load()}function switchKind(){query.current=1;load()}async function showDetail(row){detail.value=kind.value==='operations'?await getOperationLog(row.logId):row;detailVisible.value=true}onMounted(load)
</script>
<style scoped>.log-page{display:flex;flex-direction:column;gap:16px}.page-header{display:flex;justify-content:space-between;align-items:flex-end}.page-header h1{margin:0 0 6px;font-size:24px}.page-header p{color:var(--color-ink-muted)}pre{white-space:pre-wrap;word-break:break-all;margin:0}</style>
