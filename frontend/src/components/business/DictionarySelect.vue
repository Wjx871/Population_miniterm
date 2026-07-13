<template>
  <el-select
    :model-value="modelValue"
    :disabled="disabled"
    :placeholder="placeholder"
    :clearable="clearable"
    filterable
    @update:model-value="handleChange"
  >
    <el-option
      v-for="item in options"
      :key="item.value"
      :label="item.label"
      :value="item.value"
    />
  </el-select>
</template>

<script setup>
import { ref, onMounted, watch } from 'vue'
import { getCachedDictionary } from '../../services/referenceDataCache.js'

const props = defineProps({
  modelValue: {
    type: [String, Number],
    default: ''
  },
  type: {
    type: String,
    required: true
  },
  disabled: {
    type: Boolean,
    default: false
  },
  placeholder: {
    type: String,
    default: '请选择'
  },
  clearable: {
    type: Boolean,
    default: true
  }
})

const emit = defineEmits(['update:modelValue', 'change'])

const options = ref([])

const loadOptions = async () => {
  if (props.type) {
    options.value = await getCachedDictionary(props.type, false) // active only
  }
}

onMounted(loadOptions)
watch(() => props.type, loadOptions)

const handleChange = (val) => {
  emit('update:modelValue', val)
  emit('change', val)
}
</script>
