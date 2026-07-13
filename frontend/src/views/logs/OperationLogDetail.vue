<template>
  <div class="page-container" v-loading="loading">
    <div class="page-header">
      <div>
        <h1>操作日志详情</h1>
        <p class="subtitle">仅展示服务端已脱敏字段；无删除入口。</p>
      </div>
      <el-button @click="router.back()">返回</el-button>
    </div>

    <el-card v-if="detail" shadow="never">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="日志 ID">{{ detail.logId }}</el-descriptions-item>
        <el-descriptions-item label="用户 ID">{{ detail.userId ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="操作人">{{ detail.username || '-' }}</el-descriptions-item>
        <el-descriptions-item label="操作类型">{{ detail.operationType || '-' }}</el-descriptions-item>
        <el-descriptions-item label="业务模块">{{ detail.moduleName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="结果">{{ detail.operationResult || '-' }}</el-descriptions-item>
        <el-descriptions-item label="请求方法">{{ detail.requestMethod || '-' }}</el-descriptions-item>
        <el-descriptions-item label="IP">{{ detail.ipAddress || '-' }}</el-descriptions-item>
        <el-descriptions-item label="请求路径" :span="2">{{ detail.requestPath || '-' }}</el-descriptions-item>
        <el-descriptions-item label="操作时间" :span="2">{{ formatDateTime(detail.operationTime) }}</el-descriptions-item>
        <el-descriptions-item label="错误信息" :span="2">{{ detail.errorMessage || '-' }}</el-descriptions-item>
        <el-descriptions-item label="详情" :span="2">
          <pre class="detail-pre">{{ detail.detail || '-' }}</pre>
        </el-descriptions-item>
      </el-descriptions>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getOperationLogDetail } from '../../api/logs'
import { normalizeLogRecord } from '../../adapters/log'
import { formatDateTime } from '../../utils/date'
import { getApiErrorMessage } from '../../utils/apiError'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const detail = ref(null)
const logId = computed(() => route.params.id)

async function load() {
  loading.value = true
  try {
    detail.value = normalizeLogRecord(await getOperationLogDetail(logId.value))
  } catch (error) {
    ElMessage.error(getApiErrorMessage(error, '加载日志详情失败'))
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
.detail-pre {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: inherit;
}
</style>
