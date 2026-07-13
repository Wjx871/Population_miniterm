<template>
  <el-cascader
    :model-value="modelValue"
    :options="options"
    :props="cascaderProps"
    :disabled="effectiveDisabled"
    :placeholder="loadFailed ? '加载失败' : placeholder"
    :clearable="clearable"
    filterable
    @update:model-value="handleChange"
  />
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
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

const emit = defineEmits(['update:modelValue', 'change', 'load-error'])

const options = ref([])
const cascaderProps = ref({
  value: 'value',
  label: 'label',
  children: 'children',
  emitPath: false,
  checkStrictly: props.checkStrictly
})

const loading = ref(false)
const loadFailed = ref(false)
const requestId = ref(0)
let disposed = false

const effectiveDisabled = computed(() => props.disabled || loading.value || loadFailed.value)

onUnmounted(() => {
  disposed = true
})

async function loadOptions() {
  const currentRequest = ++requestId.value
  loading.value = true
  loadFailed.value = false

  try {
    const result = await getCachedRegionTree(false)

    if (!disposed && currentRequest === requestId.value) {
      options.value = result
    }
  } catch (error) {
    if (!disposed && currentRequest === requestId.value) {
      options.value = []
      loadFailed.value = true
      ElMessage.error('加载行政区划失败')
      emit('load-error', error)
    }
  } finally {
    if (!disposed && currentRequest === requestId.value) {
      loading.value = false
    }
  }
}

onMounted(loadOptions)

const handleChange = (val) => {
  emit('update:modelValue', val)
  emit('change', val)
}
</script>
