import { ATTENTION_LEVEL, KEY_POPULATION_STATUS } from '../constants/keyPopulation.js'

const pickFirst = (...values) => {
  for (const v of values) {
    if (v !== undefined && v !== null) return v
  }
  return undefined
}

export function normalizeKeyPopulationRecord(raw) {
  if (!raw) return null
  return {
    recordId: pickFirst(raw.recordId, raw.id),
    personId: raw.personId,
    personName: raw.personName || '',
    idCard: raw.idCard || raw.identityNo || '',
    phone: raw.phone || '',
    address: raw.address || '',
    regionCode: raw.regionCode || '',
    populationType: raw.populationType || '',
    attentionLevel: raw.attentionLevel || '',
    registerReason: raw.registerReason || '',
    registerDate: raw.registerDate || '',
    responsibleDepartmentId: raw.responsibleDepartmentId,
    responsibleUserId: raw.responsibleUserId,
    status: raw.status || '',
    releaseReason: raw.releaseReason || '',
    releaseDate: raw.releaseDate || '',
    sourceApplicationId: raw.sourceApplicationId,
    version: raw.version,
    createdAt: raw.createdAt || '',
    updatedAt: raw.updatedAt || ''
  }
}

export function normalizeKeyPopulationList(records) {
  if (!Array.isArray(records)) return []
  return records.filter((r) => r != null).map(normalizeKeyPopulationRecord)
}

/**
 * KeyPopulationDetailView:
 * { application: KeyApplicationView, materials, approvalLogs }
 * KeyApplicationView:
 * { application: ApplicationView, detailId, operationType, ..., version, executable }
 */
export function normalizeKeyPopulationApplication(raw) {
  if (!raw) return null
  const keyApp = raw.application || {}
  const appView = keyApp.application || null
  const professional = {
    detailId: keyApp.detailId,
    operationType: keyApp.operationType,
    recordId: keyApp.recordId,
    personId: keyApp.personId,
    populationType: keyApp.populationType,
    attentionLevel: keyApp.attentionLevel,
    reason: keyApp.reason,
    eventDate: keyApp.eventDate,
    responsibleDepartmentId: keyApp.responsibleDepartmentId,
    responsibleUserId: keyApp.responsibleUserId,
    businessStatus: keyApp.businessStatus,
    version: keyApp.version
  }
  return {
    application: appView,
    professional,
    materials: raw.materials || [],
    approvalLogs: raw.approvalLogs || [],
    executable: keyApp.executable,
    unavailableReason: keyApp.executable === false ? '当前状态不可执行' : ''
  }
}

export function toCreateRegisterPayload(form) {
  return {
    personId: form.personId,
    populationType: form.populationType,
    attentionLevel: form.attentionLevel,
    registerReason: form.registerReason,
    registerDate: form.registerDate,
    responsibleDepartmentId: form.responsibleDepartmentId || null,
    responsibleUserId: form.responsibleUserId || null,
    title: form.title,
    remark: form.remark || null
  }
}

export function toCreateReleasePayload(form) {
  return {
    releaseReason: form.releaseReason,
    releaseDate: form.releaseDate,
    title: form.title,
    remark: form.remark || null
  }
}

export function toKeyPopulationQueryParams(query) {
  const params = { current: query.current, size: query.size }
  const keys = [
    'personId',
    'personName',
    'idCard',
    'populationType',
    'attentionLevel',
    'status',
    'responsibleDepartmentId',
    'regionCode',
    'registerDateFrom',
    'registerDateTo'
  ]
  for (const key of keys) {
    if (query[key] !== undefined && query[key] !== null && query[key] !== '') {
      params[key] = query[key]
    }
  }
  return params
}

export function getKeyPopulationDetailFields(detail) {
  const pro = detail?.professional
  if (!pro) return []
  return [
    { label: '操作类型', value: pro.operationType === 'RELEASE' ? '解除' : '建档' },
    { label: '人员 ID', value: pro.personId ?? '-' },
    { label: '重点类型', value: pro.populationType || '-' },
    {
      label: '关注等级',
      value: ATTENTION_LEVEL[pro.attentionLevel] || pro.attentionLevel || '-'
    },
    { label: '原因', value: pro.reason || '-', span: 2 },
    { label: '事件日期', value: pro.eventDate || '-' },
    { label: '业务状态', value: pro.businessStatus || '-' },
    { label: '责任部门', value: pro.responsibleDepartmentId ?? '-' },
    { label: '责任人', value: pro.responsibleUserId ?? '-' },
    { label: '关联记录', value: pro.recordId ?? '-' },
    { label: '版本号', value: pro.version ?? '-' }
  ]
}

export function formatAttentionLevel(level) {
  return ATTENTION_LEVEL[level] || level || '-'
}

export function formatKeyStatus(status) {
  return KEY_POPULATION_STATUS[status] || status || '-'
}

export function normalizeHistoryList(records) {
  if (!Array.isArray(records)) return []
  return records.map((raw) => ({
    historyId: pickFirst(raw.historyId, raw.id),
    recordId: raw.recordId,
    eventType: raw.eventType || raw.historyEvent || '',
    eventDate: raw.eventDate || '',
    reason: raw.reason || raw.eventReason || '',
    operatorId: raw.operatorId,
    createdAt: raw.createdAt || '',
    // 快照字段按后端返回原样展示，不猜测敏感字段
    snapshot: raw.snapshot || raw
  }))
}
