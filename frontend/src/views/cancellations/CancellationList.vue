<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <h1>注销管理</h1>
        <p class="subtitle">综合查询注销业务；审批通过后须授权经办人显式执行。</p>
      </div>
      <div class="header-actions">
        <el-button
          v-if="canCreatePerson"
          type="primary"
          :icon="Plus"
          @click="goApply('PERSON')"
        >
          人员注销
        </el-button>
        <el-button
          v-if="canCreateHousehold"
          type="primary"
          plain
          :icon="Plus"
          @click="goApply('HOUSEHOLD')"
        >
          家庭户销户
        </el-button>
        <el-button v-if="canViewArchive" @click="router.push('/household-archives')">
          家庭户归档
        </el-button>
      </div>
    </div>

    <SearchPanel @search="fetchList" @reset="reset">
      <el-form :inline="true" :model="query" label-width="88px">
        <el-form-item label="注销编号">
          <el-input v-model.trim="query.cancellationNo" clearable style="width: 160px" />
        </el-form-item>
        <el-form-item label="对象类型">
          <el-select v-model="query.cancelObjectType" clearable style="width: 140px">
            <el-option
              v-for="(label, value) in CANCEL_OBJECT_TYPE_LABEL"
              :key="value"
              :label="label"
              :value="value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="注销原因">
          <el-select v-model="query.cancelReasonCode" clearable filterable style="width: 160px">
            <el-option
              v-for="(label, value) in CANCELLATION_REASON_LABEL"
              :key="value"
              :label="label"
              :value="value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="人员姓名">
          <el-input v-model.trim="query.personName" clearable style="width: 140px" />
        </el-form-item>
        <el-form-item label="身份证号">
          <el-input v-model.trim="query.identityNo" clearable style="width: 180px" />
        </el-form-item>
        <el-form-item label="家庭户号">
          <el-input v-model.trim="query.householdNo" clearable style="width: 140px" />
        </el-form-item>
        <el-form-item label="业务状态">
          <el-select v-model="query.businessStatus" clearable style="width: 140px">
            <el-option
              v-for="(label, value) in CANCELLATION_BUSINESS_STATUS"
              :key="value"
              :label="label"
              :value="value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="行政区划">
          <el-input v-model.trim="query.regionCode" clearable style="width: 140px" />
        </el-form-item>
        <el-form-item label="事件日期">
          <el-date-picker
            v-model="eventRange"
            type="daterange"
            value-format="YYYY-MM-DD"
            start-placeholder="开始"
            end-placeholder="结束"
            style="width: 240px"
          />
        </el-form-item>
      </el-form>
    </SearchPanel>

    <el-card shadow="never">
      <el-table :data="records" v-loading="loading" border stripe>
        <el-table-column prop="cancellationNo" label="注销编号" width="160" />
        <el-table-column label="对象类型" width="110">
          <template #default="{ row }">
            {{ CANCEL_OBJECT_TYPE_LABEL[row.cancelObjectType] || row.cancelObjectType || '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="personName" label="人员" width="100" />
        <el-table-column label="身份证号" width="180">
          <template #default="{ row }">
            <SensitiveText :value="row.identityNo" kind="idCard" />
          </template>
        </el-table-column>
        <el-table-column prop="householdNo" label="户号" width="120" />
        <el-table-column label="注销原因" min-width="120">
          <template #default="{ row }">
            {{ CANCELLATION_REASON_LABEL[row.cancelReasonCode] || row.cancelReasonCode || '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="eventDate" label="事件日期" width="120" />
        <el-table-column label="业务状态" width="110">
          <template #default="{ row }">
            <StatusTag :value="row.businessStatus" kind="application" />
          </template>
        </el-table-column>
        <el-table-column prop="regionCode" label="区划" width="100" />
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.applicationId"
              link
              type="primary"
              @click="viewApplication(row)"
            >
              查看申请
            </el-button>
            <el-button
              v-if="row.businessStatus === 'DRAFT' && row.applicationId && canEditDraft(row)"
              link
              type="primary"
              @click="editDraft(row)"
            >
              编辑
            </el-button>
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
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { Plus } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import SearchPanel from '../../components/common/SearchPanel.vue'
import AppPagination from '../../components/common/AppPagination.vue'
import StatusTag from '../../components/common/StatusTag.vue'
import SensitiveText from '../../components/common/SensitiveText.vue'
import { getCancellationPage } from '../../api/cancellations'
import {
  normalizeCancellationList,
  toCancellationQueryParams
} from '../../adapters/cancellation'
import {
  CANCEL_OBJECT_TYPE_LABEL,
  CANCELLATION_REASON_LABEL,
  CANCELLATION_BUSINESS_STATUS
} from '../../constants/cancellation'
import { PERMISSIONS } from '../../constants/permissions'
import { useUserStore } from '../../stores/user'
import { normalizePageResult } from '../../utils/page'
import { getApiErrorMessage } from '../../utils/apiError'

const router = useRouter()
const userStore = useUserStore()

const loading = ref(false)
const records = ref([])
const total = ref(0)
const eventRange = ref([])
const query = reactive({
  cancellationNo: '',
  cancelObjectType: '',
  cancelReasonCode: '',
  personName: '',
  identityNo: '',
  householdNo: '',
  businessStatus: '',
  regionCode: '',
  eventFrom: '',
  eventTo: '',
  current: 1,
  size: 10
})

const canCreatePerson = computed(() => userStore.hasPermission(PERMISSIONS.CANCELLATION_PERSON_CREATE))
const canCreateHousehold = computed(() => userStore.hasPermission(PERMISSIONS.CANCELLATION_HOUSEHOLD_CREATE))
const canViewArchive = computed(() => userStore.hasPermission(PERMISSIONS.CANCELLATION_ARCHIVE_VIEW))

watch(eventRange, (range) => {
  query.eventFrom = range?.[0] || ''
  query.eventTo = range?.[1] || ''
})

function canEditDraft(row) {
  if (!userStore.hasPermission(PERMISSIONS.APPLICATION_EDIT)) return false
  if (row.cancelObjectType === 'HOUSEHOLD') {
    return userStore.hasPermission(PERMISSIONS.CANCELLATION_HOUSEHOLD_CREATE)
  }
  return userStore.hasPermission(PERMISSIONS.CANCELLATION_PERSON_CREATE)
}

async function fetchList() {
  loading.value = true
  try {
    const page = normalizePageResult(await getCancellationPage(toCancellationQueryParams(query)))
    records.value = normalizeCancellationList(page.records)
    total.value = page.total
  } catch (error) {
    ElMessage.error(getApiErrorMessage(error, '加载注销列表失败'))
  } finally {
    loading.value = false
  }
}

function reset() {
  Object.assign(query, {
    cancellationNo: '',
    cancelObjectType: '',
    cancelReasonCode: '',
    personName: '',
    identityNo: '',
    householdNo: '',
    businessStatus: '',
    regionCode: '',
    eventFrom: '',
    eventTo: '',
    current: 1
  })
  eventRange.value = []
  fetchList()
}

function goApply(objectType) {
  router.push({ path: '/cancellations/apply', query: { objectType } })
}

function viewApplication(row) {
  router.push(`/applications/${row.applicationId}`)
}

function editDraft(row) {
  router.push({
    path: '/cancellations/apply',
    query: {
      applicationId: row.applicationId,
      objectType: row.cancelObjectType || 'PERSON'
    }
  })
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
