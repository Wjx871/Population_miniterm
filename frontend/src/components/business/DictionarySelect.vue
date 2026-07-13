<template>
  <el-select
    :model-value="modelValue"
    :disabled="effectiveDisabled"
    :loading="loading"
    :placeholder="loadFailed ? '加载失败' : placeholder"
    :clearable="clearable"
    filterable
    @update:model-value="handleChange"
  >
    <!-- 保留历史数据的展示：同时识别编码与名称 -->
    <el-option
      v-if="modelValue && !options.some(matchesModelValue)"
      :label="modelValue"
      :value="modelValue"
      style="display: none"
    />
    <el-option
      v-for="item in options"
      :key="item.value"
      :label="item.label"
      :value="getOptionValue(item)"
    />
  </el-select>
</template>

<script setup>
import { ref, computed, onMounted, watch, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getCachedDictionary } from '../../services/referenceDataCache.js'
import { resolveDictionaryOptionValue } from '../../adapters/dictionary.js'

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
  },
  /**
   * code  默认，提交 dictCode
   * label 提交 dictName（用于民族等历史字段保存中文名）
   */
  valueMode: {
    type: String,
    default: 'code',
    validator: (value) => ['code', 'label'].includes(value),
  },
})

const emit = defineEmits(['update:modelValue', 'change', 'load-error'])

const options = ref([])
const loading = ref(false)
const loadFailed = ref(false)
const requestId = ref(0)
let disposed = false

const effectiveDisabled = computed(() => props.disabled || loading.value || loadFailed.value)

function getOptionValue(item) {
  return resolveDictionaryOptionValue(item, props.valueMode)
}

function matchesModelValue(item) {
  return item.value === props.modelValue || item.label === props.modelValue
}

onUnmounted(() => {
  disposed = true
})

async function loadOptions() {
  if (!props.type) return
  const currentRequest = ++requestId.value
  loading.value = true
  loadFailed.value = false

  try {
    const result = await getCachedDictionary(props.type, false)

    if (!disposed && currentRequest === requestId.value) {
      options.value = result
    }
  } catch (error) {
    if (!disposed && currentRequest === requestId.value) {
      options.value = []
      loadFailed.value = true
      ElMessage.error(`加载字典(${props.type})失败`)
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
