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
      v-for="p in options"
      :key="p.id"
      :label="formatLabel(p)"
      :value="p.id"
    />
  </el-select>
</template>

<script setup>
import { ref, watch } from 'vue'
import { getPersonPage, getPersonById } from '../../api/persons'
import { normalizePerson, normalizePersonList } from '../../adapters/person'
import { normalizePageResult } from '../../utils/page'
import { maskIdCard } from '../../utils/mask'

const props = defineProps({
  modelValue: {
    type: [Number, String],
    default: null,
  },
  placeholder: {
    type: String,
    default: '请输入姓名或身份证号搜索',
  },
  disabled: {
    type: Boolean,
    default: false,
  },
  /** 传给分页筛选；空字符串表示不限状态 */
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

function formatLabel(person) {
  if (!person) return ''
  const name = person.name || '未知'
  return `${name} · ${maskIdCard(person.idCard)}`
}

function isIdCardLike(keyword) {
  const text = String(keyword || '').trim()
  return /^[0-9Xx]{15,18}$/.test(text)
}

function mergeOptions(list) {
  const excludeSet = new Set((props.excludeIds || []).map((id) => String(id)))
  const map = new Map()

  list.forEach((item) => {
    if (!item?.id) return
    if (excludeSet.has(String(item.id))) return
    map.set(String(item.id), item)
  })

  // 已选项不在当前结果时仍保留显示
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

async function fetchPersons(keyword = '') {
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
      if (isIdCardLike(text)) {
        query.idCard = text
      } else {
        query.name = text
      }
    }

    const res = await getPersonPage(query)
    if (seq !== requestSeq) return

    const page = normalizePageResult(res)
    const list = normalizePersonList(page.records)
    mergeOptions(list)
  } catch (error) {
    if (seq !== requestSeq) return
    console.error('人员远程搜索失败', error)
    // 保留已选项，避免清空导致表单显示异常
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
    fetchPersons(keyword)
  }, 300)
}

function onVisibleChange(visible) {
  if (visible && options.value.length === 0) {
    fetchPersons('')
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
    const res = await getPersonById(id)
    const person = normalizePerson(res)
    if (person.id != null) {
      selectedCache.value.set(String(person.id), person)
      mergeOptions(options.value)
    }
  } catch (error) {
    console.error('加载已选人员失败', error)
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
