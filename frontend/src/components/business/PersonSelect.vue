<template>
  <el-select 
    :model-value="modelValue"
    @update:model-value="handleUpdate"
    filterable 
    placeholder="请选择人员" 
    style="width: 100%"
    clearable
  >
    <el-option 
      v-for="p in personList" 
      :key="p.id || p.personId" 
      :label="`${p.name} (${p.idCard})`" 
      :value="p.id || p.personId"
    />
  </el-select>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { getPersonList } from '../../api/persons';

defineProps({
  modelValue: {
    type: [Number, String],
    default: null
  }
});

const emit = defineEmits(['update:modelValue']);

const personList = ref([]);

onMounted(async () => {
  try {
    const res = await getPersonList({ size: 1000, status: '正常' });
    personList.value = res.records || res.content || res || [];
  } catch (error) {
    console.error('加载人员列表失败', error);
  }
});

const handleUpdate = (val) => {
  emit('update:modelValue', val);
};
</script>
