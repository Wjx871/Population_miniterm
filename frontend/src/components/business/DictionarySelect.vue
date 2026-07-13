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
import { ref, onMounted, watch, onUnmounted } from 'vue'
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

const emit = defineEmits(['update:modelValue', 'change', 'load-error'])

const options = ref([])
const loading = ref(false)
const requestId = ref(0)
let disposed = false

onUnmounted(() => {
  disposed = true
})

async function loadOptions() {
  if (!props.type) return
  const currentRequest = ++requestId.value
  loading.value = true

  try {
    const result = await getCachedDictionary(props.type, false)

    if (!disposed && currentRequest === requestId.value) {
      options.value = result
    }
  } catch (error) {
    if (!disposed && currentRequest === requestId.value) {
      options.value = []
      emit('load-error', error)
    }
  } finally {
    if (!disposed && currentRequest === requestId.value) {
      loading.value = false
    }
  }
}

onMounted(loadOptions)
watch(() => props.type, loadOptions)

const handleChange = (val) => {
  emit('update:modelValue', val)
  emit('change', val)
}
</script>
