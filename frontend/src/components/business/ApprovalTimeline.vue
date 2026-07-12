<template>
  <el-timeline v-if="sortedLogs.length">
    <el-timeline-item v-for="log in sortedLogs" :key="log.logId" :timestamp="formatDateTime(log.operationTime)" placement="top">
      <el-card shadow="never"><strong>{{ actionLabel(log.action) }}</strong><span v-if="log.fromStatus || log.toStatus">：{{ statusLabel(log.fromStatus) }} → {{ statusLabel(log.toStatus) }}</span><p>操作人：{{ log.operatorUserId ? `用户 #${log.operatorUserId}` : '-' }}</p><p v-if="log.comment">{{ log.comment }}</p></el-card>
    </el-timeline-item>
  </el-timeline>
  <el-empty v-else description="暂无审批轨迹" :image-size="72" />
</template>

<script setup>
import { computed } from 'vue'
import { formatDateTime } from '../../utils/date'
import { APPLICATION_STATUS, APPROVAL_STATUS } from '../../constants/application'
const props = defineProps({ logs: { type: Array, default: () => [] } })
const ACTION_LABEL = Object.freeze({ SUBMIT: '提交申请', APPROVE: '审批通过', REJECT: '审批驳回', WITHDRAW: '撤回申请', CANCEL: '取消申请', EXECUTE: '业务执行完成' })
const sortedLogs = computed(() => [...props.logs].sort((a, b) => new Date(a.operationTime || 0) - new Date(b.operationTime || 0)))
function actionLabel(action) { return ACTION_LABEL[action] || action || '状态变更' }
function statusLabel(status) { return APPLICATION_STATUS[status] || APPROVAL_STATUS[status] || status || '-' }
</script>
