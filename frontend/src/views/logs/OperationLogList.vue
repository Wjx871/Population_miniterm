<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <h1>操作日志</h1>
        <p class="subtitle">只读查询，按数据范围过滤；不提供删除，不展示密码/JWT/完整敏感信息。</p>
      </div>
    </div>

    <SearchPanel @search="fetchList" @reset="reset">
      <el-form :inline="true" :model="query" label-width="88px">
        <el-form-item label="操作人">
          <el-input v-model.trim="query.username" clearable style="width: 140px" />
        </el-form-item>
        <el-form-item label="操作类型">
          <el-input v-model.trim="query.operationType" clearable style="width: 160px" />
        </el-form-item>
        <el-form-item label="业务模块">
          <el-input v-model.trim="query.module" clearable style="width: 140px" />
        </el-form-item>
        <el-form-item label="结果">
          <el-input v-model.trim="query.result" clearable style="width: 120px" />
        </el-form-item>
        <el-form-item label="IP">
          <el-input v-model.trim="query.ip" clearable style="width: 140px" />
        </el-form-item>
        <el-form-item label="时间范围">
          <el-date-picker
            v-model="dateRange"
            type="datetimerange"
            value-format="YYYY-MM-DDTHH:mm:ss"
            start-placeholder="开始"
            end-placeholder="结束"
            style="width: 360px"
          />
        </el-form-item>
      </el-form>
    </SearchPanel>

    <el-card shadow="never">
      <el-table :data="records" v-loading="loading" border stripe>
        <el-table-column prop="logId" label="ID" width="80" />
        <el-table-column prop="username" label="操作人" width="120" />
        <el-table-column prop="operationType" label="操作类型" min-width="140" />
        <el-table-column prop="moduleName" label="模块" width="120" />
        <el-table-column prop="requestMethod" label="方法" width="80" />
        <el-table-column prop="requestPath" label="路径" min-width="160" show-overflow-tooltip />
        <el-table-column prop="operationResult" label="结果" width="100" />
        <el-table-column prop="ipAddress" label="IP" width="130" />
        <el-table-column label="时间" min-width="170">
          <template #default="{ row }">{{ formatDateTime(row.operationTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="router.push(`/logs/operations/${row.logId}`)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
      <AppPagination v-model:current="query.current" v-model:size="query.size" :total="total" @change="fetchList" />
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import SearchPanel from '../../components/common/SearchPanel.vue'
import AppPagination from '../../components/common/AppPagination.vue'
import { getOperationLogs } from '../../api/logs'
import { normalizeLogList, toOperationLogQueryParams } from '../../adapters/log'
import { normalizePageResult } from '../../utils/page'
import { formatDateTime } from '../../utils/date'
import { getApiErrorMessage } from '../../utils/apiError'

const router = useRouter()
const loading = ref(false)
const records = ref([])
const total = ref(0)
const dateRange = ref([])
const query = reactive({
  username: '',
  operationType: '',
  module: '',
  result: '',
  ip: '',
  dateFrom: '',
  dateTo: '',
  current: 1,
  size: 10
})

watch(dateRange, (range) => {
  query.dateFrom = range?.[0] || ''
  query.dateTo = range?.[1] || ''
})

async function fetchList() {
  loading.value = true
  try {
    const page = normalizePageResult(await getOperationLogs(toOperationLogQueryParams(query)))
    records.value = normalizeLogList(page.records)
    total.value = page.total
  } catch (error) {
    ElMessage.error(getApiErrorMessage(error, '加载操作日志失败'))
  } finally {
    loading.value = false
  }
}

function reset() {
  Object.assign(query, {
    username: '',
    operationType: '',
    module: '',
    result: '',
    ip: '',
    dateFrom: '',
    dateTo: '',
    current: 1
  })
  dateRange.value = []
  fetchList()
}

onMounted(fetchList)
</script>

<style scoped>
.page-container { display: flex; flex-direction: column; gap: 16px; }
.page-header h1 { margin: 0 0 8px; }
.subtitle { margin: 0; color: var(--el-text-color-secondary); }
</style>
