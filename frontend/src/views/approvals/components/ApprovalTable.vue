<template>
  <el-table :data="rows" v-loading="loading" border stripe>
    <el-table-column prop="approvalNo" label="审批编号" width="180" />
    <el-table-column prop="applicationNo" label="申请编号" width="180" />
    <el-table-column prop="title" label="申请标题" min-width="180" show-overflow-tooltip />
    <el-table-column prop="applicantName" label="申请人" width="120" />
    <el-table-column label="状态" width="120">
      <template #default="{ row }"><StatusTag :value="row.status" kind="approval" /></template>
    </el-table-column>
    <el-table-column label="提交时间" min-width="170">
      <template #default="{ row }">{{ formatDateTime(row.submittedAt) }}</template>
    </el-table-column>
    <el-table-column label="操作" width="90" fixed="right">
      <template #default="{ row }"><el-button link type="primary" @click="emit('view', row)">查看</el-button></template>
    </el-table-column>
  </el-table>
</template>

<script setup>
import StatusTag from '../../../components/common/StatusTag.vue'
import { formatDateTime } from '../../../utils/date'

defineProps({
  rows: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
})
const emit = defineEmits(['view'])
</script>
