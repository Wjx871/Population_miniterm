<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <h1>登录日志</h1>
        <p class="subtitle">展示登录成功/失败/登出等结果；禁止显示密码与 JWT。</p>
      </div>
    </div>

    <SearchPanel @search="fetchList" @reset="reset">
      <el-form :inline="true" :model="query" label-width="88px">
        <el-form-item label="用户名">
          <el-input v-model.trim="query.username" clearable style="width: 140px" />
        </el-form-item>
        <el-form-item label="结果">
          <el-select v-model="query.result" clearable style="width: 160px">
            <el-option label="LOGIN_SUCCESS" value="LOGIN_SUCCESS" />
            <el-option label="LOGIN_FAILED" value="LOGIN_FAILED" />
            <el-option label="LOGOUT" value="LOGOUT" />
          </el-select>
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
        <el-table-column prop="username" label="用户名" width="140" />
        <el-table-column prop="operationType" label="类型" width="140" />
        <el-table-column prop="operationResult" label="结果" width="140" />
        <el-table-column prop="ipAddress" label="IP" width="140" />
        <el-table-column label="时间" min-width="180">
          <template #default="{ row }">{{ formatDateTime(row.operationTime) }}</template>
        </el-table-column>
        <el-table-column prop="errorMessage" label="说明" min-width="160" show-overflow-tooltip />
      </el-table>
      <AppPagination v-model:current="query.current" v-model:size="query.size" :total="total" @change="fetchList" />
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import SearchPanel from '../../components/common/SearchPanel.vue'
import AppPagination from '../../components/common/AppPagination.vue'
import { getLoginLogs } from '../../api/logs'
import { normalizeLogList, toLoginLogQueryParams } from '../../adapters/log'
import { normalizePageResult } from '../../utils/page'
import { formatDateTime } from '../../utils/date'
import { getApiErrorMessage } from '../../utils/apiError'

const loading = ref(false)
const records = ref([])
const total = ref(0)
const dateRange = ref([])
const query = reactive({
  username: '',
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
    const page = normalizePageResult(await getLoginLogs(toLoginLogQueryParams(query)))
    records.value = normalizeLogList(page.records)
    total.value = page.total
  } catch (error) {
    ElMessage.error(getApiErrorMessage(error, '加载登录日志失败'))
  } finally {
    loading.value = false
  }
}

function reset() {
  Object.assign(query, {
    username: '',
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
