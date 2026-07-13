<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <h1>重点人口管理</h1>
        <p class="subtitle">建档与解除均走申请-审批-显式执行；无物理删除。</p>
      </div>
      <el-button v-if="canApply" type="primary" :icon="Plus" @click="router.push('/key-population/register')">
        建档申请
      </el-button>
    </div>

    <SearchPanel @search="fetchList" @reset="reset">
      <el-form :inline="true" :model="query" label-width="88px">
        <el-form-item label="姓名">
          <el-input v-model.trim="query.personName" clearable style="width: 140px" />
        </el-form-item>
        <el-form-item label="身份证号">
          <el-input v-model.trim="query.idCard" clearable style="width: 180px" />
        </el-form-item>
        <el-form-item label="重点类型">
          <DictionarySelect v-model="query.populationType" type="KEY_POPULATION_TYPE" clearable style="width: 160px" />
        </el-form-item>
        <el-form-item label="关注等级">
          <el-select v-model="query.attentionLevel" clearable style="width: 120px">
            <el-option v-for="(label, value) in ATTENTION_LEVEL" :key="value" :label="label" :value="value" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" clearable style="width: 120px">
            <el-option v-for="(label, value) in KEY_POPULATION_STATUS" :key="value" :label="label" :value="value" />
          </el-select>
        </el-form-item>
        <el-form-item label="行政区划">
          <el-input v-model.trim="query.regionCode" clearable style="width: 140px" />
        </el-form-item>
        <el-form-item label="建档日期">
          <el-date-picker
            v-model="registerRange"
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
        <el-table-column prop="personName" label="姓名" width="100" />
        <el-table-column label="身份证号" width="180">
          <template #default="{ row }"><SensitiveText :value="row.idCard" kind="idCard" /></template>
        </el-table-column>
        <el-table-column prop="populationType" label="重点类型" min-width="120" />
        <el-table-column label="关注等级" width="100">
          <template #default="{ row }">{{ formatAttentionLevel(row.attentionLevel) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }"><StatusTag :value="row.status" kind="application" /></template>
        </el-table-column>
        <el-table-column prop="registerDate" label="建档日期" width="120" />
        <el-table-column prop="regionCode" label="区划" width="100" />
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="router.push(`/key-population/${row.recordId}`)">详情</el-button>
            <el-button link @click="router.push(`/key-population/${row.recordId}/history`)">历史</el-button>
            <el-button
              v-if="canApply && row.status === 'ACTIVE'"
              link
              type="warning"
              @click="router.push(`/key-population/${row.recordId}/release`)"
            >
              解除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <AppPagination v-model:current="query.current" v-model:size="query.size" :total="total" @change="fetchList" />
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
import DictionarySelect from '../../components/business/DictionarySelect.vue'
import { getKeyPopulationPage } from '../../api/keyPopulation'
import {
  formatAttentionLevel,
  normalizeKeyPopulationList,
  toKeyPopulationQueryParams
} from '../../adapters/keyPopulation'
import { ATTENTION_LEVEL, KEY_POPULATION_STATUS } from '../../constants/keyPopulation'
import { PERMISSIONS } from '../../constants/permissions'
import { useUserStore } from '../../stores/user'
import { normalizePageResult } from '../../utils/page'
import { getApiErrorMessage } from '../../utils/apiError'

const router = useRouter()
const userStore = useUserStore()
const loading = ref(false)
const records = ref([])
const total = ref(0)
const registerRange = ref([])

const query = reactive({
  personName: '',
  idCard: '',
  populationType: '',
  attentionLevel: '',
  status: '',
  regionCode: '',
  registerDateFrom: '',
  registerDateTo: '',
  current: 1,
  size: 10
})

const canApply = computed(() => userStore.hasPermission(PERMISSIONS.KEY_POPULATION_APPLY))

watch(registerRange, (range) => {
  query.registerDateFrom = range?.[0] || ''
  query.registerDateTo = range?.[1] || ''
})

async function fetchList() {
  loading.value = true
  try {
    const page = normalizePageResult(await getKeyPopulationPage(toKeyPopulationQueryParams(query)))
    records.value = normalizeKeyPopulationList(page.records)
    total.value = page.total
  } catch (error) {
    ElMessage.error(getApiErrorMessage(error, '加载重点人口列表失败'))
  } finally {
    loading.value = false
  }
}

function reset() {
  Object.assign(query, {
    personName: '',
    idCard: '',
    populationType: '',
    attentionLevel: '',
    status: '',
    regionCode: '',
    registerDateFrom: '',
    registerDateTo: '',
    current: 1
  })
  registerRange.value = []
  fetchList()
}

onMounted(fetchList)
</script>

<style scoped>
.page-container { display: flex; flex-direction: column; gap: 16px; }
.page-header { display: flex; justify-content: space-between; align-items: end; gap: 12px; }
.page-header h1 { margin: 0 0 8px; }
.subtitle { margin: 0; color: var(--el-text-color-secondary); }
</style>
