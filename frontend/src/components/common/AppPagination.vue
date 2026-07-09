<template>
  <div class="pagination-container">
    <el-pagination
      v-model:current-page="internalCurrent"
      v-model:page-size="internalSize"
      :page-sizes="[10, 20, 50, 100]"
      layout="total, sizes, prev, pager, next, jumper"
      :total="total"
      @size-change="handleSizeChange"
      @current-change="handleCurrentChange"
    />
  </div>
</template>

<script setup>
import { computed } from 'vue';

const props = defineProps({
  current: {
    type: Number,
    default: 1
  },
  size: {
    type: Number,
    default: 10
  },
  total: {
    type: Number,
    default: 0
  }
});

const emit = defineEmits(['update:current', 'update:size', 'change']);

const internalCurrent = computed({
  get: () => props.current,
  set: (val) => emit('update:current', val)
});

const internalSize = computed({
  get: () => props.size,
  set: (val) => emit('update:size', val)
});

const handleSizeChange = (val) => {
  emit('update:size', val);
  emit('change');
};

const handleCurrentChange = (val) => {
  emit('update:current', val);
  emit('change');
};
</script>

<style scoped>
.pagination-container {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
