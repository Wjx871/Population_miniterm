export function normalizeDictionaryItem(item) {
  if (!item) return null
  return {
    value: item.dictCode,
    label: item.dictName,
    status: item.status
  }
}

export function normalizeDictionaryList(items) {
  if (!Array.isArray(items)) return []
  return items
    .map(normalizeDictionaryItem)
    .filter(item => item && (item.status === 'ACTIVE' || item.status === '正常' || item.status === '启用')) // 只加载启用项，兼容可能的不同表示
}
