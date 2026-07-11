<template>
  <div class="permit-timeline">
    <el-timeline v-if="logs && logs.length > 0">
      <el-timeline-item
        v-for="(log, index) in sortedLogs"
        :key="index"
        :timestamp="log.operatedAt || log.createdAt || ''"
        :type="actionType(log.action)"
        placement="top"
      >
        <div class="timeline-item-header">
          <el-tag :type="actionType(log.action)" size="small">{{ LIFECYCLE_ACTION[log.action] || log.action }}</el-tag>
          <span class="status-change" v-if="log.statusChange">{{ log.statusChange }}</span>
        </div>
        <div class="timeline-item-body" v-if="log.oldValidFrom || log.oldValidUntil || log.newValidFrom || log.newValidUntil">
          <span v-if="log.oldValidFrom || log.oldValidUntil" class="valid-info">
            原有效期：{{ log.oldValidFrom || '-' }} ~ {{ log.oldValidUntil || '-' }}
          </span>
          <span v-if="log.newValidFrom || log.newValidUntil" class="valid-info arrow">
            → 新有效期：{{ log.newValidFrom || '-' }} ~ {{ log.newValidUntil || '-' }}
          </span>
        </div>
        <div class="timeline-item-footer" v-if="log.reason">
          原因：{{ log.reason }}
        </div>
        <div class="timeline-item-footer" v-if="log.operatorId">
          操作人ID：{{ log.operatorId }}
        </div>
        <div class="timeline-item-footer" v-if="log.sourceApplicationId">
          关联申请：{{ log.sourceApplicationId }}
        </div>
      </el-timeline-item>
    </el-timeline>
    <el-empty v-else description="暂无生命周期记录" />
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { LIFECYCLE_ACTION } from '../../constants/floatingResidence'

const props = defineProps({
  logs: {
    type: Array,
    default: () => []
  }
})

const sortedLogs = computed(() => {
  return [...props.logs].sort((a, b) => {
    const ta = a.operatedAt || a.createdAt || ''
    const tb = b.operatedAt || b.createdAt || ''
    return ta.localeCompare(tb)
  })
})

function actionType(action) {
  const map = {
    ISSUE: 'success',
    ENDORSE: 'primary',
    CANCEL: 'danger',
    EXPIRE: 'warning',
    REGISTRATION_CLOSED: 'info'
  }
  return map[action] || 'info'
}
</script>

<style scoped>
.permit-timeline{padding:4px 0}
.timeline-item-header{display:flex;align-items:center;gap:8px;margin-bottom:4px}
.status-change{font-size:13px;color:var(--el-text-color-secondary)}
.timeline-item-body{font-size:13px;color:var(--el-text-color-regular);margin-bottom:2px}
.valid-info.arrow{margin-left:8px}
.timeline-item-footer{font-size:12px;color:var(--el-text-color-secondary)}
</style>
