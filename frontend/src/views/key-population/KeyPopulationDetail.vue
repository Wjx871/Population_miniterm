<template>
  <div class="page-container" v-loading="loading">
    <div class="page-header">
      <div>
        <h1>重点人口详情</h1>
        <p class="subtitle">敏感字段按后端返回展示；解除须走申请审批流程。</p>
      </div>
      <div class="header-actions">
        <el-button @click="router.push(`/key-population/${recordId}/history`)">历史</el-button>
        <el-button
          v-if="canApply && detail?.status === 'ACTIVE'"
          type="warning"
          @click="router.push(`/key-population/${recordId}/release`)"
        >
          解除申请
        </el-button>
        <el-button @click="goBackOrFallback(router, '/key-population')">返回</el-button>
      </div>
    </div>

    <el-card v-if="detail" shadow="never">
      <template #header>
        <div class="card-header">
          <span>{{ detail.personName || `记录 #${detail.recordId}` }}</span>
          <StatusTag :value="detail.status" kind="application" />
        </div>
      </template>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="人员姓名">{{ detail.personName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="身份证号">
          <SensitiveText :value="detail.idCard" kind="idCard" />
        </el-descriptions-item>
        <el-descriptions-item label="手机号">
          <SensitiveText :value="detail.phone" kind="phone" />
        </el-descriptions-item>
        <el-descriptions-item label="行政区划">{{ detail.regionCode || '-' }}</el-descriptions-item>
        <el-descriptions-item label="地址" :span="2">
          <SensitiveText :value="detail.address" kind="text" />
        </el-descriptions-item>
        <el-descriptions-item label="重点类型">{{ detail.populationType || '-' }}</el-descriptions-item>
        <el-descriptions-item label="关注等级">{{ formatAttentionLevel(detail.attentionLevel) }}</el-descriptions-item>
        <el-descriptions-item label="建档原因" :span="2">{{ detail.registerReason || '-' }}</el-descriptions-item>
        <el-descriptions-item label="建档日期">{{ detail.registerDate || '-' }}</el-descriptions-item>
        <el-descriptions-item label="责任部门">{{ detail.responsibleDepartmentId ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="解除原因" :span="2">{{ detail.releaseReason || '-' }}</el-descriptions-item>
        <el-descriptions-item label="解除日期">{{ detail.releaseDate || '-' }}</el-descriptions-item>
        <el-descriptions-item label="来源申请">
          <el-button
            v-if="detail.sourceApplicationId"
            link
            type="primary"
            @click="router.push(`/applications/${detail.sourceApplicationId}`)"
          >
            #{{ detail.sourceApplicationId }}
          </el-button>
          <span v-else>-</span>
        </el-descriptions-item>
        <el-descriptions-item label="版本号">{{ detail.version ?? '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import StatusTag from '../../components/common/StatusTag.vue'
import SensitiveText from '../../components/common/SensitiveText.vue'
import { getKeyPopulationDetail } from '../../api/keyPopulation'
import { formatAttentionLevel, normalizeKeyPopulationRecord } from '../../adapters/keyPopulation'
import { PERMISSIONS } from '../../constants/permissions'
import { useUserStore } from '../../stores/user'
import { getApiErrorMessage } from '../../utils/apiError'
import { goBackOrFallback } from '../../utils/navigation'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const loading = ref(false)
const detail = ref(null)
const recordId = computed(() => route.params.recordId)
const canApply = computed(() => userStore.hasPermission(PERMISSIONS.KEY_POPULATION_APPLY))

async function load() {
  loading.value = true
  try {
    detail.value = normalizeKeyPopulationRecord(await getKeyPopulationDetail(recordId.value))
  } catch (error) {
    ElMessage.error(getApiErrorMessage(error, '加载重点人口详情失败'))
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.page-container { display: flex; flex-direction: column; gap: 16px; }
.page-header { display: flex; justify-content: space-between; align-items: end; gap: 12px; flex-wrap: wrap; }
.page-header h1 { margin: 0 0 8px; }
.subtitle { margin: 0; color: var(--el-text-color-secondary); }
.header-actions, .card-header { display: flex; align-items: center; gap: 8px; }
.card-header { justify-content: space-between; }
</style>
