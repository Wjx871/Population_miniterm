<template>
  <el-card v-if="fields && fields.length" shadow="never" class="professional-fields-panel">
    <template #header>
      <div class="card-header">
        <span>{{ title }}</span>
        <StatusTag v-if="status" :value="status" :kind="statusKind" />
      </div>
    </template>
    <el-descriptions :column="2" border>
      <el-descriptions-item
        v-for="(field, index) in fields"
        :key="`${field.label}-${index}`"
        :label="field.label"
        :span="field.span || 1"
      >
        {{ field.value ?? '-' }}
      </el-descriptions-item>
    </el-descriptions>
    <el-alert
      v-if="unavailableReason"
      type="warning"
      :closable="false"
      show-icon
      class="unavailable-reason"
      :title="unavailableReason"
    />
  </el-card>
</template>

<script setup>
import StatusTag from '../common/StatusTag.vue'

defineProps({
  title: { type: String, default: '专业业务信息' },
  fields: { type: Array, default: () => [] },
  status: { type: String, default: '' },
  statusKind: { type: String, default: 'application' },
  unavailableReason: { type: String, default: '' }
})
</script>

<style scoped>
.professional-fields-panel { margin-top: 0; }
.card-header { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.unavailable-reason { margin-top: 12px; }
</style>
