<template>
  <el-select
    :model-value="modelValue"
    :placeholder="placeholder"
    :disabled="disabled"
    :clearable="clearable"
    :loading="loading"
    filterable
    remote
    reserve-keyword
    :remote-method="onRemoteSearch"
    style="width: 100%"
    @update:model-value="handleUpdate"
    @visible-change="onVisibleChange"
    @clear="handleClear"
  >
    <el-option
      v-for="item in options"
      :key="item.id"
      :label="formatLabel(item)"
      :value="item.id"
    />
  </el-select>
</template>

<script setup>
/**
 * 家庭户远程选择器（结构完成）。
 * 接口契约待后端确认；当前无 HouseholdController 时搜索会失败并展示空列表，禁止 Mock。
 */
import { ref, watch } from 'vue'
import { getHouseholdPage, getHouseholdById } from '../../api/households'
import { normalizeHousehold, normalizeHouseholdList } from '../../adapters/household'
import { normalizePageResult } from '../../utils/page'

const props = defineProps({
  modelValue: {
    type: [Number, String],
    default: null,
  },
  placeholder: {
    type: String,
    default: '请输入户籍编号或户主姓名搜索',
  },
  disabled: {
    type: Boolean,
    default: false,
  },
  status: {
    type: String,
    default: '正常',
  },
  excludeIds: {
    type: Array,
    default: () => [],
  },
  clearable: {
    type: Boolean,
    default: true,
  },
  size: {
    type: Number,
    default: 20,
  },
})

const emit = defineEmits(['update:modelValue', 'change', 'select'])

const loading = ref(false)
const options = ref([])
const selectedCache = ref(new Map())
let debounceTimer = null
let requestSeq = 0

const maxSize = () => Math.min(20, Math.max(1, Number(props.size) || 20))

function formatLabel(item) {
  if (!item) return ''
  const no = item.householdNo || '无编号'
  const head = item.headPersonName || '未知户主'
  const address = item.address ? String(item.address).slice(0, 20) : '无地址'
  return `${no} · ${head} · ${address}`
}

function mergeOptions(list) {
  const excludeSet = new Set((props.excludeIds || []).map((id) => String(id)))
  const map = new Map()

  list.forEach((item) => {
    if (!item?.id) return
    if (excludeSet.has(String(item.id))) return
    map.set(String(item.id), item)
  })

  if (props.modelValue != null && props.modelValue !== '') {
    const key = String(props.modelValue)
    if (!excludeSet.has(key)) {
      const cached = selectedCache.value.get(key)
      if (cached && !map.has(key)) {
        map.set(key, cached)
      }
    }
  }

  options.value = Array.from(map.values())
}

async function fetchHouseholds(keyword = '') {
  const seq = ++requestSeq
  loading.value = true
  try {
    const query = {
      current: 1,
      size: maxSize(),
    }
    if (props.status) {
      query.status = props.status
    }

    const text = String(keyword || '').trim()
    if (text) {
      // 接口契约待后端确认：常见筛选为户号或户主姓名
      if (/^\d+$/.test(text) || text.length >= 6) {
        query.householdNo = text
      } else {
        query.headPersonName = text
      }
    }

    const res = await getHouseholdPage(query)
    if (seq !== requestSeq) return

    const page = normalizePageResult(res)
    const list = normalizeHouseholdList(page.records)
    mergeOptions(list)
  } catch (error) {
    if (seq !== requestSeq) return
    console.error('家庭户远程搜索失败', error)
    mergeOptions([])
  } finally {
    if (seq === requestSeq) {
      loading.value = false
    }
  }
}

function onRemoteSearch(keyword) {
  if (debounceTimer) clearTimeout(debounceTimer)
  debounceTimer = setTimeout(() => {
    fetchHouseholds(keyword)
  }, 300)
}

function onVisibleChange(visible) {
  if (visible && options.value.length === 0) {
    fetchHouseholds('')
  }
}

async function ensureSelectedOption(id) {
  if (id === null || id === undefined || id === '') return
  const key = String(id)
  if (selectedCache.value.has(key)) {
    mergeOptions(options.value)
    return
  }
  if (options.value.some((p) => String(p.id) === key)) {
    const found = options.value.find((p) => String(p.id) === key)
    selectedCache.value.set(key, found)
    return
  }

  try {
    const res = await getHouseholdById(id)
    const item = normalizeHousehold(res)
    if (item.id != null) {
      selectedCache.value.set(String(item.id), item)
      mergeOptions(options.value)
    }
  } catch (error) {
    console.error('加载已选家庭户失败', error)
  }
}

function handleUpdate(val) {
  emit('update:modelValue', val)
  const selected = options.value.find((p) => String(p.id) === String(val))
    || selectedCache.value.get(String(val))
    || null
  if (selected) {
    selectedCache.value.set(String(selected.id), selected)
  }
  emit('change', val)
  emit('select', selected)
}

function handleClear() {
  emit('update:modelValue', null)
  emit('change', null)
  emit('select', null)
}

watch(
  () => props.modelValue,
  (val) => {
    ensureSelectedOption(val)
  },
  { immediate: true }
)

watch(
  () => props.excludeIds,
  () => {
    mergeOptions(options.value)
  },
  { deep: true }
)
</script>
