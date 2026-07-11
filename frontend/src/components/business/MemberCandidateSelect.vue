<template>
  <el-select :model-value="modelValue" clearable placeholder="请选择新户主" style="width: 100%" @update:model-value="$emit('update:modelValue', $event)">
    <el-option v-for="personId in candidateIds" :key="personId" :label="`家庭成员 #${personId}`" :value="personId" />
  </el-select>
</template>

<script setup>
defineProps({ modelValue: { type: [Number, String], default: null }, candidateIds: { type: Array, default: () => [] } })
defineEmits(['update:modelValue'])
</script>
