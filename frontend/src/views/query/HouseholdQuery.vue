<template>
  <div class="query-page">
    <div class="page-header"><div><h1>家庭户综合查询</h1><p>结果受当前账户数据范围限制。</p></div><el-button :icon="Refresh" @click="load">刷新</el-button></div>
    <SearchPanel @search="search" @reset="reset">
      <el-form :inline="true" :model="query">
        <el-form-item label="户号"><el-input v-model.trim="query.householdNo" clearable /></el-form-item>
        <el-form-item label="户主"><el-input v-model.trim="query.headPersonName" clearable /></el-form-item>
        <el-form-item label="地址"><el-input v-model.trim="query.address" clearable /></el-form-item>
        <el-form-item label="区划"><RegionCascader v-model="query.regionCode" style="width:180px" /></el-form-item>
        <el-form-item label="户类型"><DictionarySelect v-model="query.householdType" type="HOUSEHOLD_TYPE" style="width:160px" /></el-form-item>
        <el-form-item label="状态"><el-select v-model="query.status" clearable style="width:140px"><el-option label="有效" value="ACTIVE"/><el-option label="待销户" value="PENDING_CANCELLATION"/></el-select></el-form-item>
        <el-form-item label="含重点人口"><el-select v-model="query.containsKeyPopulation" clearable style="width:140px"><el-option label="是" :value="true"/><el-option label="否" :value="false"/></el-select></el-form-item>
      </el-form>
    </SearchPanel>
    <el-alert v-if="error" title="家庭户查询失败" type="error" show-icon :closable="false"><el-button link type="primary" @click="load">重试</el-button></el-alert>
    <el-card shadow="never">
      <el-table v-loading="loading" :data="records" empty-text="查询成功，暂无匹配数据">
        <el-table-column prop="householdNo" label="户号" min-width="150"/><el-table-column prop="headPersonName" label="户主" min-width="110"/>
        <el-table-column prop="address" label="地址" min-width="210" show-overflow-tooltip/><el-table-column prop="regionCode" label="区划编码" min-width="120"/>
        <el-table-column prop="householdType" label="户类型" min-width="110"/><el-table-column prop="memberCount" label="成员数" width="90"/>
        <el-table-column label="含重点人口" width="110"><template #default="{row}">{{ row.containsKeyPopulation === null ? '-' : (row.containsKeyPopulation ? '是' : '否') }}</template></el-table-column>
        <el-table-column prop="status" label="状态" min-width="120"/><el-table-column label="操作" width="90"><template #default="{row}"><el-button link type="primary" @click="$router.push(`/households/${row.householdId}`)">详情</el-button></template></el-table-column>
      </el-table>
      <AppPagination v-model:current="query.current" v-model:size="query.size" :total="total" @change="load"/>
    </el-card>
  </div>
</template>
<script setup>
import { onMounted, reactive, ref } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import SearchPanel from '../../components/common/SearchPanel.vue'
import AppPagination from '../../components/common/AppPagination.vue'
import RegionCascader from '../../components/business/RegionCascader.vue'
import DictionarySelect from '../../components/business/DictionarySelect.vue'
import { queryHouseholds } from '../../api/query.js'
import { normalizeHouseholdQueryPage } from '../../adapters/query.js'
const initial = () => ({ householdNo:'', headPersonName:'', address:'', regionCode:'', householdType:'', status:'', containsKeyPopulation:null, current:1, size:10 })
const query = reactive(initial()); const records=ref([]); const total=ref(0); const loading=ref(false); const error=ref(false)
async function load(){loading.value=true;try{const page=normalizeHouseholdQueryPage(await queryHouseholds(query));records.value=page.records;total.value=page.total;error.value=false}catch{error.value=true}finally{loading.value=false}}
function search(){query.current=1;load()} function reset(){Object.assign(query,initial());load()} onMounted(load)
</script>
<style scoped>.query-page{display:flex;flex-direction:column;gap:16px}.page-header{display:flex;justify-content:space-between;align-items:flex-end}.page-header h1{margin:0 0 6px;font-size:24px}.page-header p{color:var(--color-ink-muted)}</style>
