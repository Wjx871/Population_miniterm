<template>
  <el-timeline v-if="logs.length">
    <el-timeline-item v-for="log in logs" :key="log.logId" :timestamp="formatDateTime(log.operationTime)" placement="top">
      <el-card shadow="never"><strong>{{ log.action }}</strong><span v-if="log.fromStatus || log.toStatus">：{{ log.fromStatus || '-' }} → {{ log.toStatus || '-' }}</span><p v-if="log.comment">{{ log.comment }}</p></el-card>
    </el-timeline-item>
  </el-timeline>
  <el-empty v-else description="暂无审批轨迹" :image-size="72" />
</template>

<script setup>
import { formatDateTime } from '../../utils/date'
defineProps({ logs: { type: Array, default: () => [] } })
</script>
