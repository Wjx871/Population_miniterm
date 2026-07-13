export function isEnabledStatus(status) {
  return status === 'ENABLED' || status === 'ACTIVE' || status === '正常' || status === '启用'
}

export function normalizeDictionaryItem(item) {
  if (!item) return null
  const status = item.status || 'DISABLED'
  const isActive = isEnabledStatus(status)
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

export function toDictionaryQueryParams(query) {
  return {
    dictionaryType: query.dictType || undefined,
    dictionaryCode: query.dictCode || undefined,
    keyword: query.keyword || undefined,
    status: query.status || undefined,
    page: Math.max((query.current || 1) - 1, 0),
    size: query.size || 10,
  }
}

export function toDictionaryCreatePayload(form) {
  return {
    dictionaryType: form.dictType,
    dictionaryCode: form.dictCode,
    displayName: form.dictName,
    sortNo: form.sortNo,
  }
}

export function toDictionaryUpdatePayload(form) {
  return {
    displayName: form.dictName,
    sortNo: form.sortNo,
    version: form.version
  }
}

/**
 * 根据 valueMode 解析字典选项提交值
 * @param {{ value?: string, label?: string } | null | undefined} item
 * @param {'code' | 'label'} [valueMode='code']
 * @returns {string | undefined}
 */
export function resolveDictionaryOptionValue(item, valueMode = 'code') {
  return valueMode === 'label' ? item?.label : item?.value
}
