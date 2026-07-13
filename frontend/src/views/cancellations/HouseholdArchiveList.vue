<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <h1>家庭户归档</h1>
        <p class="subtitle">归档为只读历史快照，不提供编辑、恢复或删除入口。</p>
      </div>
      <el-button @click="router.push('/cancellations')">返回注销管理</el-button>
    </div>

    <SearchPanel @search="fetchList" @reset="reset">
      <el-form :inline="true" :model="query" label-width="88px">
        <el-form-item label="户号">
          <el-input v-model.trim="query.householdNo" clearable style="width: 160px" />
        </el-form-item>
        <el-form-item label="户主姓名">
          <el-input v-model.trim="query.headPersonName" clearable style="width: 140px" />
        </el-form-item>
        <el-form-item label="行政区划">
          <el-input v-model.trim="query.regionCode" clearable style="width: 140px" />
        </el-form-item>
        <el-form-item label="销户原因">
          <el-select v-model="query.reasonCode" clearable style="width: 160px">
            <el-option
              v-for="(label, value) in HOUSEHOLD_CANCELLATION_REASON"
              :key="value"
              :label="label"
              :value="value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="归档时间">
          <el-date-picker
            v-model="archivedRange"
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
        <el-table-column prop="archiveId" label="归档 ID" width="100" />
        <el-table-column prop="householdNo" label="原户号" width="140" />
        <el-table-column prop="headPersonName" label="户主" width="120" />
        <el-table-column prop="regionCode" label="区划" width="110" />
        <el-table-column prop="registeredAddress" label="登记地址" min-width="180" show-overflow-tooltip />
        <el-table-column label="销户原因" min-width="120">
          <template #default="{ row }">
            {{ HOUSEHOLD_CANCELLATION_REASON[row.cancellationReasonCode] || row.cancellationReasonCode || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="归档时间" min-width="170">
          <template #default="{ row }">{{ formatDateTime(row.archivedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="open(row)">查看</el-button>
          </template>
        </el-table-column>
      </el-table>
      <AppPagination
        v-model:current="query.current"
        v-model:size="query.size"
        :total="total"
        @change="fetchList"
      />
    </el-card>

    <DetailDrawer v-model="drawerVisible" title="家庭户归档详情" :loading="detailLoading">
      <el-descriptions v-if="detail" :column="1" border>
        <el-descriptions-item label="归档 ID">{{ detail.archiveId }}</el-descriptions-item>
        <el-descriptions-item label="原家庭户 ID">{{ detail.originalHouseholdId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="关联申请">{{ detail.applicationId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="关联注销">{{ detail.cancellationId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="原户号">{{ detail.householdNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="户主">{{ detail.headPersonName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="户主 ID">{{ detail.headPersonId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="登记地址">{{ detail.registeredAddress || '-' }}</el-descriptions-item>
        <el-descriptions-item label="行政区划">{{ detail.regionCode || '-' }}</el-descriptions-item>
        <el-descriptions-item label="户类型">{{ detail.householdType || '-' }}</el-descriptions-item>
        <el-descriptions-item label="立户日期">{{ detail.establishDate || '-' }}</el-descriptions-item>
        <el-descriptions-item label="原状态">{{ detail.originalStatus || '-' }}</el-descriptions-item>
        <el-descriptions-item label="销户原因">
          {{ HOUSEHOLD_CANCELLATION_REASON[detail.cancellationReasonCode] || detail.cancellationReasonCode || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="原因说明">{{ detail.cancellationReasonDetail || '-' }}</el-descriptions-item>
        <el-descriptions-item label="归档时间">{{ formatDateTime(detail.archivedAt) }}</el-descriptions-item>
      </el-descriptions>
    </DetailDrawer>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import SearchPanel from '../../components/common/SearchPanel.vue'
import AppPagination from '../../components/common/AppPagination.vue'
import DetailDrawer from '../../components/common/DetailDrawer.vue'
import {
  getHouseholdArchiveDetail,
  getHouseholdArchivePage
} from '../../api/cancellations'
import {
  normalizeHouseholdArchive,
  normalizeHouseholdArchiveList,
  toHouseholdArchiveQueryParams
} from '../../adapters/cancellation'
import { HOUSEHOLD_CANCELLATION_REASON } from '../../constants/cancellation'
import { normalizePageResult } from '../../utils/page'
import { formatDateTime } from '../../utils/date'
import { getApiErrorMessage } from '../../utils/apiError'

const router = useRouter()
const loading = ref(false)
const records = ref([])
const total = ref(0)
const archivedRange = ref([])
const drawerVisible = ref(false)
const detailLoading = ref(false)
const detail = ref(null)

const query = reactive({
  householdNo: '',
  headPersonName: '',
  regionCode: '',
  reasonCode: '',
  archivedFrom: '',
  archivedTo: '',
  current: 1,
  size: 10
})

watch(archivedRange, (range) => {
  query.archivedFrom = range?.[0] || ''
  query.archivedTo = range?.[1] || ''
})

async function fetchList() {
  loading.value = true
  try {
    const page = normalizePageResult(await getHouseholdArchivePage(toHouseholdArchiveQueryParams(query)))
    records.value = normalizeHouseholdArchiveList(page.records)
    total.value = page.total
  } catch (error) {
    ElMessage.error(getApiErrorMessage(error, '加载家庭户归档失败'))
  } finally {
    loading.value = false
  }
}

function reset() {
  Object.assign(query, {
    householdNo: '',
    headPersonName: '',
    regionCode: '',
    reasonCode: '',
    archivedFrom: '',
    archivedTo: '',
    current: 1
  })
  archivedRange.value = []
  fetchList()
}

async function open(row) {
  drawerVisible.value = true
  detailLoading.value = true
  try {
    detail.value = normalizeHouseholdArchive(await getHouseholdArchiveDetail(row.archiveId))
  } catch (error) {
    ElMessage.error(getApiErrorMessage(error, '加载归档详情失败'))
  } finally {
    detailLoading.value = false
  }
}

onMounted(fetchList)
</script>

<style scoped>
.page-container { display: flex; flex-direction: column; gap: 16px; }
.page-header { display: flex; justify-content: space-between; align-items: end; gap: 12px; }
.page-header h1 { margin: 0 0 8px; }
.subtitle { margin: 0; color: var(--el-text-color-secondary); }
</style>
