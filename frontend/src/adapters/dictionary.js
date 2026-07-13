export function normalizeDictionaryItem(item) {
  if (!item) return null
  const status = item.status || 'DISABLED'
  const isActive = status === 'ACTIVE' || status === '正常' || status === '启用'
  return {
    id: item.dictId,
    type: item.dictType,
    value: item.dictCode,
    label: item.dictName,
    sortNo: item.sortNo || 0,
    status: status,
    version: item.version || 0,
    disabled: !isActive
  }
}

export function normalizeDictionaryList(items, includeInactive = false) {
  if (!Array.isArray(items)) return []
  const normalized = items.map(normalizeDictionaryItem).filter(Boolean)
  if (includeInactive) return normalized
  return normalized.filter(item => !item.disabled)
}
