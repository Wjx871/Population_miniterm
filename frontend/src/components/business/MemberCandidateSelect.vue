<template>
  <el-select :model-value="modelValue" :disabled="disabled" clearable placeholder="请选择新户主" style="width: 100%" @update:model-value="$emit('update:modelValue', $event)">
    <el-option v-for="candidate in candidates" :key="candidate.personId" :label="formatLabel(candidate)" :value="candidate.personId" />
  </el-select>
</template>

<script setup>
import { maskIdCard } from '../../utils/mask'
const props = defineProps({ modelValue: { type: [Number, String], default: null }, candidates: { type: Array, default: () => [] }, disabled: Boolean })
defineEmits(['update:modelValue'])

function formatLabel(candidate) {
  return `${candidate.name || `成员 #${candidate.personId}`} · ${maskIdCard(candidate.idCard)}`
}
</script>
