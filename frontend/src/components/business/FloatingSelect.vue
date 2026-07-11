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
      :key="item.floatingId"
      :label="formatLabel(item)"
      :value="item.floatingId"
    />
  </el-select>
</template>

<script setup>
import { ref, watch } from 'vue'
import { getFloatingPopulationPage } from '../../api/floatingResidence'
import { normalizeFloatingList } from '../../adapters/floating'
import { normalizePageResult } from '../../utils/page'
import { maskIdCard } from '../../utils/mask'

const props = defineProps({
  modelValue: {
    type: [Number, String],
    default: null
  },
  placeholder: {
    type: String,
    default: '请搜索流动登记记录'
  },
  disabled: {
    type: Boolean,
    default: false
  },
  status: {
    type: String,
    default: 'ACTIVE'
  },
  clearable: {
    type: Boolean,
    default: true
  }
})

const emit = defineEmits(['update:modelValue', 'change', 'select'])

const MAX_SIZE = 20
const loading = ref(false)
const options = ref([])
const selectedCache = ref(new Map())
let debounceTimer = null
let requestSeq = 0

function formatLabel(item) {
  if (!item) return ''
  const name = item.personName || '未知'
  const region = item.currentRegionCode || ''
  return `${item.registrationNo || '-'} · ${name} · ${maskIdCard(item.identityNo)} · ${region}`
}

function isIdCardLike(keyword) {
  const text = String(keyword || '').trim()
  return /^[0-9Xx]{15,18}$/.test(text)
}

function mergeOptions(list) {
  const map = new Map()
  list.forEach((item) => {
    if (!item?.floatingId) return
    map.set(String(item.floatingId), item)
  })
  // 已选项不在当前结果时仍保留
  if (props.modelValue != null && props.modelValue !== '') {
    const key = String(props.modelValue)
    const cached = selectedCache.value.get(key)
    if (cached && !map.has(key)) {
      map.set(key, cached)
    }
  }
  options.value = Array.from(map.values())
}

async function fetchList(keyword = '') {
  const seq = ++requestSeq
  loading.value = true
  try {
    const query = { current: 1, size: MAX_SIZE }
    if (props.status) query.status = props.status

    const text = String(keyword || '').trim()
    if (text) {
      if (isIdCardLike(text)) {
        query.identityNo = text
      } else {
        query.personName = text
      }
    }

    const res = await getFloatingPopulationPage(query)
    if (seq !== requestSeq) return

    const page = normalizePageResult(res)
    const list = normalizeFloatingList(page.records)
    mergeOptions(list)
  } catch (error) {
    if (seq !== requestSeq) return
    console.error('流动登记远程搜索失败', error)
    mergeOptions([])
  } finally {
    if (seq === requestSeq) loading.value = false
  }
}

function onRemoteSearch(keyword) {
  if (debounceTimer) clearTimeout(debounceTimer)
  debounceTimer = setTimeout(() => fetchList(keyword), 300)
}

function onVisibleChange(visible) {
  if (visible && options.value.length === 0) fetchList('')
}

async function ensureSelectedOption(id) {
  if (id === null || id === undefined || id === '') return
  const key = String(id)
  if (selectedCache.value.has(key)) {
    mergeOptions(options.value)
    return
  }
  if (options.value.some((item) => String(item.floatingId) === key)) {
    const found = options.value.find((item) => String(item.floatingId) === key)
    selectedCache.value.set(key, found)
    return
  }
  try {
    const { getFloatingPopulationById } = await import('../../api/floatingResidence')
    const res = await getFloatingPopulationById(id)
    const item = (await import('../../adapters/floating')).normalizeFloatingPopulation(res)
    if (item.floatingId != null) {
      selectedCache.value.set(String(item.floatingId), item)
      mergeOptions(options.value)
    }
  } catch (error) {
    console.error('加载已选流动登记失败', error)
  }
}

function handleUpdate(val) {
  emit('update:modelValue', val)
  const selected = options.value.find((item) => String(item.floatingId) === String(val))
    || selectedCache.value.get(String(val))
    || null
  if (selected) selectedCache.value.set(String(selected.floatingId), selected)
  emit('change', val)
  emit('select', selected)
}

function handleClear() {
  emit('update:modelValue', null)
  emit('change', null)
  emit('select', null)
}

watch(() => props.modelValue, (val) => { ensureSelectedOption(val) }, { immediate: true })
</script>
