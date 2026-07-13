<template>
  <div class="page-container" v-loading="loading">
    <div class="page-header">
      <div>
        <h1>重点人口历史</h1>
        <p class="subtitle">历史只读追加；快照仅展示后端返回字段。</p>
      </div>
      <el-button @click="router.back()">返回</el-button>
    </div>

    <el-card shadow="never">
      <el-table :data="records" border stripe>
        <el-table-column prop="eventType" label="事件" width="120">
          <template #default="{ row }">{{ KEY_HISTORY_EVENT[row.eventType] || row.eventType || '-' }}</template>
        </el-table-column>
        <el-table-column prop="eventDate" label="事件日期" width="140" />
        <el-table-column prop="reason" label="原因" min-width="180" show-overflow-tooltip />
        <el-table-column prop="operatorId" label="操作人" width="100" />
        <el-table-column prop="createdAt" label="记录时间" min-width="170">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && !records.length" description="暂无历史事件" />
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getKeyPopulationHistory } from '../../api/keyPopulation'
import { normalizeHistoryList } from '../../adapters/keyPopulation'
import { KEY_HISTORY_EVENT } from '../../constants/keyPopulation'
import { formatDateTime } from '../../utils/date'
import { getApiErrorMessage } from '../../utils/apiError'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const records = ref([])
const recordId = computed(() => route.params.recordId)

async function load() {
  loading.value = true
  try {
    records.value = normalizeHistoryList(await getKeyPopulationHistory(recordId.value))
  } catch (error) {
    ElMessage.error(getApiErrorMessage(error, '加载历史失败'))
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.page-container { display: flex; flex-direction: column; gap: 16px; }
.page-header { display: flex; justify-content: space-between; align-items: end; }
.page-header h1 { margin: 0 0 8px; }
.subtitle { margin: 0; color: var(--el-text-color-secondary); }
</style>
