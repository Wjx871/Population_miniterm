<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <h1>导出记录</h1>
        <p class="subtitle">普通导出即时生成；敏感导出须申请审批后显式执行，再鉴权下载。</p>
      </div>
      <div class="header-actions">
        <el-button v-if="canNormal" type="primary" @click="router.push('/exports/normal')">普通导出</el-button>
        <el-button v-if="canSensitiveApply" type="warning" plain @click="router.push('/exports/sensitive')">敏感导出申请</el-button>
      </div>
    </div>

    <SearchPanel @search="fetchList" @reset="reset">
      <el-form :inline="true" :model="query" label-width="88px">
        <el-form-item label="导出编号">
          <el-input v-model.trim="query.exportNo" clearable style="width: 180px" />
        </el-form-item>
        <el-form-item label="模块">
          <el-select v-model="query.module" clearable style="width: 160px">
            <el-option v-for="item in moduleOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="导出类型">
          <el-select v-model="query.exportType" clearable style="width: 160px">
            <el-option label="普通脱敏" value="NORMAL_MASKED" />
            <el-option label="敏感批准" value="SENSITIVE_APPROVED" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" clearable style="width: 140px">
            <el-option v-for="(label, value) in EXPORT_STATUS" :key="value" :label="label" :value="value" />
          </el-select>
        </el-form-item>
      </el-form>
    </SearchPanel>

    <el-card shadow="never">
      <el-table :data="records" v-loading="loading" border stripe>
        <el-table-column prop="exportNo" label="导出编号" width="180" />
        <el-table-column label="模块" width="120">
          <template #default="{ row }">{{ formatExportModule(row.exportModule) }}</template>
        </el-table-column>
        <el-table-column label="类型" width="120">
          <template #default="{ row }">{{ formatExportType(row.exportType) }}</template>
        </el-table-column>
        <el-table-column prop="rowCount" label="行数" width="80" />
        <el-table-column prop="fileName" label="文件名" min-width="180" show-overflow-tooltip />
        <el-table-column label="状态" width="100">
          <template #default="{ row }"><StatusTag :value="row.status" kind="application" /></template>
        </el-table-column>
        <el-table-column prop="downloadCount" label="下载次数" width="90" />
        <el-table-column label="创建时间" min-width="160">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <el-button
              v-if="row.downloadable && row.status === 'COMPLETED'"
              link
              type="success"
              :loading="downloadingId === row.exportLogId"
              @click="handleDownload(row)"
            >
              下载
            </el-button>
            <el-button
              v-if="row.applicationId"
              link
              @click="router.push(`/applications/${row.applicationId}`)"
            >
              申请
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <AppPagination v-model:current="query.current" v-model:size="query.size" :total="total" @change="fetchList" />
    </el-card>

    <DetailDrawer v-model="drawerVisible" title="导出记录详情" :loading="detailLoading">
      <el-descriptions v-if="detail" :column="1" border>
        <el-descriptions-item label="导出编号">{{ detail.exportNo }}</el-descriptions-item>
        <el-descriptions-item label="模块">{{ formatExportModule(detail.exportModule) }}</el-descriptions-item>
        <el-descriptions-item label="类型">{{ formatExportType(detail.exportType) }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ detail.status }}</el-descriptions-item>
        <el-descriptions-item label="行数">{{ detail.rowCount ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="文件名">{{ detail.fileName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="文件大小">{{ detail.fileSize ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="导出字段">{{ detail.exportedFields || '-' }}</el-descriptions-item>
        <el-descriptions-item label="过滤条件">{{ detail.filterSnapshot || '-' }}</el-descriptions-item>
        <el-descriptions-item label="下载次数">{{ detail.downloadCount ?? 0 }}</el-descriptions-item>
        <el-descriptions-item label="最近下载">{{ formatDateTime(detail.downloadedAt) }}</el-descriptions-item>
        <el-descriptions-item label="完成时间">{{ formatDateTime(detail.completedAt) }}</el-descriptions-item>
        <el-descriptions-item label="失败原因">{{ detail.failureReason || '-' }}</el-descriptions-item>
        <el-descriptions-item label="关联申请">{{ detail.applicationId || '-' }}</el-descriptions-item>
      </el-descriptions>
      <div v-if="detail?.downloadable && detail?.status === 'COMPLETED'" style="margin-top: 12px">
        <el-button type="primary" :loading="downloadingId === detail.exportLogId" @click="handleDownload(detail)">
          鉴权下载
        </el-button>
      </div>
    </DetailDrawer>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import SearchPanel from '../../components/common/SearchPanel.vue'
import AppPagination from '../../components/common/AppPagination.vue'
import StatusTag from '../../components/common/StatusTag.vue'
import DetailDrawer from '../../components/common/DetailDrawer.vue'
import { getExportDetail, getExportPage } from '../../api/exports'
import {
  formatExportModule,
  formatExportType,
  normalizeExportLog,
  normalizeExportLogList,
  toExportQueryParams
} from '../../adapters/export'
import { EXPORT_STATUS, getModuleOptions } from '../../constants/export'
import { PERMISSIONS } from '../../constants/permissions'
import { useUserStore } from '../../stores/user'
import { downloadExportById } from '../../services/fileDownload'
import { normalizePageResult } from '../../utils/page'
import { formatDateTime } from '../../utils/date'
import { getApiErrorMessage } from '../../utils/apiError'

const router = useRouter()
const userStore = useUserStore()
const moduleOptions = getModuleOptions()

const loading = ref(false)
const records = ref([])
const total = ref(0)
const downloadingId = ref(null)
const drawerVisible = ref(false)
const detailLoading = ref(false)
const detail = ref(null)

const query = reactive({
  exportNo: '',
  module: '',
  exportType: '',
  status: '',
  current: 1,
  size: 10
})

const canNormal = computed(() => userStore.hasPermission(PERMISSIONS.DATA_EXPORT_NORMAL))
const canSensitiveApply = computed(() => userStore.hasPermission(PERMISSIONS.DATA_EXPORT_SENSITIVE_APPLY))

async function fetchList() {
  loading.value = true
  try {
    const page = normalizePageResult(await getExportPage(toExportQueryParams(query)))
    records.value = normalizeExportLogList(page.records)
    total.value = page.total
  } catch (error) {
    ElMessage.error(getApiErrorMessage(error, '加载导出记录失败'))
  } finally {
    loading.value = false
  }
}

function reset() {
  Object.assign(query, { exportNo: '', module: '', exportType: '', status: '', current: 1 })
  fetchList()
}

async function openDetail(row) {
  drawerVisible.value = true
  detailLoading.value = true
  try {
    detail.value = normalizeExportLog(await getExportDetail(row.exportLogId))
  } catch (error) {
    ElMessage.error(getApiErrorMessage(error, '加载导出详情失败'))
  } finally {
    detailLoading.value = false
  }
}

async function handleDownload(row) {
  if (downloadingId.value) return
  downloadingId.value = row.exportLogId
  try {
    await downloadExportById(row.exportLogId, row.fileName || 'export.xlsx')
    ElMessage.success('下载已开始')
    await fetchList()
    if (detail.value?.exportLogId === row.exportLogId) {
      detail.value = normalizeExportLog(await getExportDetail(row.exportLogId))
    }
  } catch (error) {
    ElMessage.error(getApiErrorMessage(error, '下载失败'))
  } finally {
    downloadingId.value = null
  }
}

onMounted(fetchList)
</script>

<style scoped>
.page-container { display: flex; flex-direction: column; gap: 16px; }
.page-header { display: flex; justify-content: space-between; align-items: end; gap: 12px; flex-wrap: wrap; }
.page-header h1 { margin: 0 0 8px; }
.subtitle { margin: 0; color: var(--el-text-color-secondary); }
.header-actions { display: flex; gap: 8px; flex-wrap: wrap; }
</style>
