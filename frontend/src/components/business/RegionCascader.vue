<template>
  <el-cascader
    :model-value="modelValue"
    :options="options"
    :props="cascaderProps"
    :disabled="disabled"
    :placeholder="placeholder"
    :clearable="clearable"
    filterable
    @update:model-value="handleChange"
  />
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getCachedRegionTree } from '../../services/referenceDataCache.js'

const props = defineProps({
  modelValue: {
    type: [String, Array],
    default: ''
  },
  disabled: {
    type: Boolean,
    default: false
  },
  placeholder: {
    type: String,
    default: '请选择行政区划'
  },
  clearable: {
    type: Boolean,
    default: true
  },
  checkStrictly: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['update:modelValue', 'change'])

const options = ref([])
const cascaderProps = ref({
  value: 'value',
  label: 'label',
  children: 'children',
  emitPath: false,
  checkStrictly: props.checkStrictly
})

onMounted(async () => {
  options.value = await getCachedRegionTree(false) // active only
})

const handleChange = (val) => {
  emit('update:modelValue', val)
  emit('change', val)
}
</script>
