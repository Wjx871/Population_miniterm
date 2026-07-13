import { EXPORT_MODULES, EXPORT_TYPE_LABEL, getFieldLabel } from '../constants/export.js'

const pickFirst = (...values) => {
  for (const v of values) {
    if (v !== undefined && v !== null) return v
  }
  return undefined
}

export function normalizeExportLog(raw) {
  if (!raw) return null
  return {
    exportLogId: pickFirst(raw.exportLogId, raw.id),
    applicationId: raw.applicationId,
    exportNo: raw.exportNo || '',
    exportModule: raw.exportModule || raw.module || '',
    exportType: raw.exportType || '',
    requestedBy: raw.requestedBy,
    executedBy: raw.executedBy,
    departmentId: raw.departmentId,
    regionCode: raw.regionCode || '',
    filterSnapshot: raw.filterSnapshot || '',
    exportedFields: raw.exportedFields || '',
    rowCount: raw.rowCount,
    fileName: raw.fileName || '',
    fileSize: raw.fileSize,
    status: raw.status || '',
    failureReason: raw.failureReason || '',
    createdAt: raw.createdAt || '',
    completedAt: raw.completedAt || '',
    downloadedAt: raw.downloadedAt || '',
    downloadCount: raw.downloadCount ?? 0,
    version: raw.version,
    downloadable: Boolean(raw.downloadable)
  }
}

export function normalizeExportLogList(records) {
  if (!Array.isArray(records)) return []
  return records.filter((r) => r != null).map(normalizeExportLog)
}

export function normalizeExportApplication(raw) {
  if (!raw) return null
  const pro = raw.professional || {}
  const fieldsRaw = pickFirst(pro.requestedFields, pro.fields, pro.exportedFields)
  const fields = typeof fieldsRaw === 'string'
    ? fieldsRaw.split(',').map((s) => s.trim()).filter(Boolean)
    : (Array.isArray(fieldsRaw) ? fieldsRaw : [])
  return {
    application: raw.application || null,
    professional: {
      requestId: pickFirst(pro.requestId, pro.id, pro.exportRequestId),
      module: pickFirst(pro.exportModule, pro.module),
      filters: pro.filters || pro.filterSnapshot || null,
      fields,
      reason: pro.reason || '',
      expectedRowLimit: pro.expectedRowLimit,
      title: pro.title || raw.application?.title || '',
      remark: pro.remark || '',
      businessStatus: pickFirst(pro.businessStatus, pro.status),
      version: pro.version
    },
    materials: raw.materials || [],
    executable: raw.executable,
    unavailableReason: raw.unavailableReason || ''
  }
}

export function toNormalExportPayload(form) {
  const filters = buildFilters(form.filters)
  return {
    module: form.module,
    filters,
    fields: [...(form.fields || [])]
  }
}

export function toSensitiveExportPayload(form) {
  return {
    module: form.module,
    filters: buildFilters(form.filters),
    fields: [...(form.fields || [])],
    reason: form.reason,
    expectedRowLimit: Number(form.expectedRowLimit),
    title: form.title,
    remark: form.remark || null
  }
}

function buildFilters(filters = {}) {
  const result = {}
  for (const key of ['name', 'regionCode', 'status', 'createdFrom', 'createdTo']) {
    const value = filters[key]
    if (value !== undefined && value !== null && value !== '') {
      result[key] = value
    }
  }
  return result
}

export function toExportQueryParams(query) {
  const params = {
    current: query.current,
    size: query.size
  }
  for (const key of ['exportNo', 'module', 'exportType', 'status']) {
    if (query[key]) params[key] = query[key]
  }
  return params
}

export function getExportDetailFields(detail) {
  const pro = detail?.professional
  if (!pro) return []
  const moduleLabel = EXPORT_MODULES[pro.module] || pro.module || '-'
  let fieldsText = '-'
  if (Array.isArray(pro.fields)) {
    fieldsText = pro.fields.map((f) => getFieldLabel(pro.module, f)).join('、') || '-'
  } else if (typeof pro.fields === 'string') {
    fieldsText = pro.fields
  }
  let filtersText = '-'
  if (pro.filters && typeof pro.filters === 'object' && !Array.isArray(pro.filters)) {
    filtersText = JSON.stringify(pro.filters)
  } else if (typeof pro.filters === 'string') {
    filtersText = pro.filters
  }
  return [
    { label: '导出模块', value: moduleLabel },
    { label: '预计上限', value: pro.expectedRowLimit ?? '-' },
    { label: '导出字段', value: fieldsText, span: 2 },
    { label: '过滤条件', value: filtersText, span: 2 },
    { label: '导出理由', value: pro.reason || '-', span: 2 },
    { label: '版本号', value: pro.version ?? '-' }
  ]
}

export function formatExportType(type) {
  return EXPORT_TYPE_LABEL[type] || type || '-'
}

export function formatExportModule(module) {
  return EXPORT_MODULES[module] || module || '-'
}
