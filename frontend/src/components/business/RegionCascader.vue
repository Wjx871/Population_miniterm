<template>
  <el-input
    v-if="usePlainInput"
    :model-value="typeof modelValue === 'string' ? modelValue : ''"
    :disabled="disabled"
    :placeholder="plainPlaceholder"
    clearable
    @update:model-value="handleChange"
  />
  <el-cascader
    v-else
    :model-value="modelValue"
    :options="options"
    :props="cascaderProps"
    :disabled="effectiveDisabled"
    :placeholder="loadFailed ? '区划暂不可用' : placeholder"
    :clearable="clearable"
    filterable
    style="width: 100%"
    @update:model-value="handleChange"
  />
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getCachedRegionTree } from '../../services/referenceDataCache.js'
import { useUserStore } from '../../stores/user'
import { PERMISSIONS } from '../../constants/permissions'

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

const userStore = useUserStore()
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
let warnedOnce = false

const canViewRegion = computed(() => userStore.hasPermission(PERMISSIONS.REGION_VIEW))
const usePlainInput = computed(() => !canViewRegion.value || loadFailed.value)
const plainPlaceholder = computed(() => {
  if (!canViewRegion.value) return '请输入行政区划代码'
  if (loadFailed.value) return '区划加载失败，可手输代码'
  return props.placeholder
})
const effectiveDisabled = computed(() => props.disabled || loading.value)

onUnmounted(() => {
  disposed = true
})

async function loadOptions() {
  if (!canViewRegion.value) {
    loadFailed.value = false
    options.value = []
    return
  }

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
      // 只提示一次，且不再叠加全局 403 弹窗
      if (!warnedOnce) {
        warnedOnce = true
        ElMessage.warning('行政区划筛选暂不可用，可手动输入区划代码继续查询')
      }
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
