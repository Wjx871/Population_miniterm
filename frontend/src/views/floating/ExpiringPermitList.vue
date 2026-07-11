<template>
  <div>
    <el-form inline style="margin-bottom:16px">
      <el-form-item label="到期窗口">
        <el-select v-model="windowDays" placeholder="请选择" @change="load">
          <el-option v-for="d in dayOptions" :key="d" :label="`${d} 天`" :value="d" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="load">查询</el-button>
      </el-form-item>
    </el-form>
    <el-alert type="warning" :closable="false" style="margin-bottom: 12px">
      查询窗口仅用于筛选，不代表后端真实签注政策。实际签注窗口以后端校验为准。
    </el-alert>

    <el-card shadow="never">
      <el-table :data="records" stripe v-loading="loading" style="width: 100%">
        <el-table-column label="证件编号" min-width="150"><template #default="{row}"><SensitiveText :value="row.permitNo" kind="text" /></template></el-table-column>
        <el-table-column prop="personName" label="姓名" min-width="100" />
        <el-table-column label="身份证号" min-width="170"><template #default="{row}"><SensitiveText :value="row.identityNo" kind="idCard" /></template></el-table-column>
        <el-table-column label="当前地址" min-width="160" show-overflow-tooltip><template #default="{row}">{{ truncateAddress(row.currentAddress) }}</template></el-table-column>
        <el-table-column prop="validUntil" label="有效期至" min-width="110" />
        <el-table-column label="剩余天数" min-width="100">
          <template #default="{row}">
            <span :style="{color: row.remainingDays < 0 ? 'var(--el-color-danger)' : row.remainingDays <= 7 ? 'var(--el-color-warning)' : ''}">
              {{ row.remainingDays !== undefined ? (row.remainingDays < 0 ? `已逾期` : `${row.remainingDays} 天`) : '-' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="状态" min-width="80"><template #default="{row}"><StatusTag :value="row.status" kind="residencePermit" /></template></el-table-column>
      </el-table>
      <AppPagination v-model:current="pager.current" v-model:size="pager.size" :total="pager.total" />
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import AppPagination from '../../components/common/AppPagination.vue'
import StatusTag from '../../components/common/StatusTag.vue'
import SensitiveText from '../../components/common/SensitiveText.vue'
import { getExpiringResidencePermits } from '../../api/floatingResidence'
import { normalizeExpiringPermitList } from '../../adapters/residencePermit'
import { normalizePageResult } from '../../utils/page'
import { getApiErrorMessage } from '../../utils/apiError'

const loading = ref(false)
const records = ref([])
const pager = reactive({ current: 1, size: 20, total: 0 })
const windowDays = ref(null)
const dayOptions = [7, 15, 30, 60]

function truncateAddress(addr) {
  if (!addr) return '-'
  return addr.length > 15 ? addr.slice(0, 15) + '…' : addr
}

async function load() {
  loading.value = true
  try {
    const params = { current: pager.current, size: pager.size }
    if (windowDays.value) params.days = windowDays.value
    const res = await getExpiringResidencePermits(params)
    const page = normalizePageResult(res)
    records.value = normalizeExpiringPermitList(page.records)
    pager.total = page.total
    pager.current = page.current
    pager.size = page.size
  } catch (error) {
    ElMessage.error(getApiErrorMessage(error, '查询到期提醒失败'))
  } finally { loading.value = false }
}

onMounted(load)
watch(() => pager.current, () => load())
watch(() => pager.size, () => { pager.current = 1; load() })
</script>
