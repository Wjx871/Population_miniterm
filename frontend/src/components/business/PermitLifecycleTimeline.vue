<template>
  <div class="permit-timeline">
    <el-timeline v-if="logs && logs.length > 0">
      <el-timeline-item
        v-for="(log, index) in sortedLogs"
        :key="index"
        :timestamp="log.operationTime || ''"
        :type="actionType(log.action)"
        placement="top"
      >
        <div class="timeline-item-header">
          <el-tag :type="actionType(log.action)" size="small">{{ LIFECYCLE_ACTION[log.action] || log.action }}</el-tag>
          <span class="status-change" v-if="log.fromStatus && log.toStatus">{{ log.fromStatus }} → {{ log.toStatus }}</span>
        </div>
        <div class="timeline-item-body" v-if="log.oldValidUntil || log.newValidUntil">
          <span v-if="log.oldValidUntil" class="valid-info">原有效期至：{{ log.oldValidUntil }}</span>
          <span v-if="log.newValidUntil" class="valid-info arrow">→ {{ log.newValidUntil }}</span>
        </div>
        <div class="timeline-item-footer" v-if="log.reason">
          原因：{{ log.reason }}
        </div>
        <div class="timeline-item-footer" v-if="log.operatorId">
          操作人ID：{{ log.operatorId }}
        </div>
        <div class="timeline-item-footer" v-if="log.applicationId">
          关联申请：{{ log.applicationId }}
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
    const ta = a.operationTime || ''
    const tb = b.operationTime || ''
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
