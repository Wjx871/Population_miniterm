import {
  CANCEL_OBJECT_TYPE_LABEL,
  CANCELLATION_REASON_LABEL,
  CANCELLATION_BUSINESS_STATUS
} from '../constants/cancellation.js'

const pickFirst = (...values) => {
  for (const v of values) {
    if (v !== undefined && v !== null) return v
  }
  return undefined
}

export function normalizeCancellationRecord(raw) {
  if (!raw) return null
  return {
    cancellationId: pickFirst(raw.cancellationId, raw.id),
    cancellationNo: raw.cancellationNo || '',
    applicationId: raw.applicationId,
    cancelObjectType: raw.cancelObjectType || '',
    personId: raw.personId,
    householdId: raw.householdId,
    cancelReasonCode: raw.cancelReasonCode || '',
    cancelReasonDetail: raw.cancelReasonDetail || '',
    eventDate: raw.eventDate || '',
    businessStatus: raw.businessStatus || '',
    personName: raw.personName || '',
    identityNo: raw.identityNo || '',
    householdNo: raw.householdNo || '',
    address: raw.address || '',
    regionCode: raw.regionCode || '',
    operatorId: raw.operatorId,
    executedAt: raw.executedAt || '',
    version: raw.version
  }
}

export function normalizeCancellationList(records) {
  if (!Array.isArray(records)) return []
  return records.filter((r) => r != null).map(normalizeCancellationRecord)
}

/**
 * 专业申请详情：对齐 CancellationDetailView，并兼容页面统一的 executable / unavailableReason。
 */
export function normalizeCancellationProfessional(raw) {
  if (!raw) return null
  const cancellation = normalizeCancellationRecord(raw.cancellation)
  return {
    application: raw.application || null,
    cancellation,
    target: raw.target || null,
    currentResidence: raw.currentResidence || null,
    activeMemberPersonIds: Array.isArray(raw.activeMemberPersonIds) ? raw.activeMemberPersonIds : [],
    materials: raw.materials || [],
    approval: raw.approval || null,
    approvalLogs: raw.approvalLogs || [],
    residenceArchive: raw.residenceArchive || null,
    householdArchive: raw.householdArchive || null,
    executable: raw.executable,
    // 后端字段名为 executionRestriction
    unavailableReason: raw.executionRestriction || raw.unavailableReason || '',
    executionRestriction: raw.executionRestriction || ''
  }
}

export function toCreatePersonCancellationPayload(form) {
  return {
    personId: form.personId,
    cancelReasonCode: form.cancelReasonCode,
    cancelReasonDetail: form.cancelReasonDetail || null,
    eventDate: form.eventDate,
    newHeadPersonId: form.newHeadPersonId || null,
    title: form.title,
    reason: form.reason,
    remark: form.remark || null
  }
}

export function toUpdatePersonCancellationPayload(form) {
  return {
    ...toCreatePersonCancellationPayload(form),
    version: form.version
  }
}

export function toCreateHouseholdCancellationPayload(form) {
  return {
    householdId: form.householdId,
    cancelReasonCode: form.cancelReasonCode,
    cancelReasonDetail: form.cancelReasonDetail || null,
    eventDate: form.eventDate,
    title: form.title,
    reason: form.reason,
    remark: form.remark || null
  }
}

export function toUpdateHouseholdCancellationPayload(form) {
  return {
    ...toCreateHouseholdCancellationPayload(form),
    version: form.version
  }
}

export function toCancellationQueryParams(query) {
  const params = {
    current: query.current,
    size: query.size
  }
  const keys = [
    'cancellationNo',
    'cancelObjectType',
    'cancelReasonCode',
    'personName',
    'identityNo',
    'householdNo',
    'businessStatus',
    'regionCode',
    'eventFrom',
    'eventTo'
  ]
  for (const key of keys) {
    const value = query[key]
    if (value !== undefined && value !== null && value !== '') {
      params[key] = value
    }
  }
  return params
}

export function toHouseholdArchiveQueryParams(query) {
  const params = {
    current: query.current,
    size: query.size
  }
  const keys = [
    'householdNo',
    'headPersonName',
    'regionCode',
    'reasonCode',
    'archivedFrom',
    'archivedTo'
  ]
  for (const key of keys) {
    const value = query[key]
    if (value !== undefined && value !== null && value !== '') {
      params[key] = value
    }
  }
  return params
}

export function normalizeHouseholdArchive(raw) {
  if (!raw) return null
  return {
    archiveId: pickFirst(raw.archiveId, raw.id),
    originalHouseholdId: raw.originalHouseholdId,
    applicationId: raw.applicationId,
    cancellationId: raw.cancellationId,
    householdNo: raw.householdNoSnapshot || raw.householdNo || '',
    headPersonId: raw.headPersonIdSnapshot || raw.headPersonId,
    headPersonName: raw.headPersonNameSnapshot || raw.headPersonName || '',
    registeredAddress: raw.registeredAddressSnapshot || raw.registeredAddress || '',
    regionCode: raw.regionCodeSnapshot || raw.regionCode || '',
    householdType: raw.householdTypeSnapshot || raw.householdType || '',
    establishDate: raw.establishDateSnapshot || raw.establishDate || '',
    originalStatus: raw.originalStatus || '',
    cancellationReasonCode: raw.cancellationReasonCode || '',
    cancellationReasonDetail: raw.cancellationReasonDetail || '',
    archivedBy: raw.archivedBy,
    archivedAt: raw.archivedAt || '',
    createdAt: raw.createdAt || ''
  }
}

export function normalizeHouseholdArchiveList(records) {
  if (!Array.isArray(records)) return []
  return records.filter((r) => r != null).map(normalizeHouseholdArchive)
}

export function getCancellationDetailFields(detail) {
  const c = detail?.cancellation
  if (!c) return []
  return [
    { label: '注销编号', value: c.cancellationNo || '-' },
    {
      label: '对象类型',
      value: CANCEL_OBJECT_TYPE_LABEL[c.cancelObjectType] || c.cancelObjectType || '-'
    },
    { label: '人员姓名', value: c.personName || '-' },
    { label: '身份证号', value: c.identityNo || '-' },
    { label: '家庭户号', value: c.householdNo || '-' },
    {
      label: '注销原因',
      value: CANCELLATION_REASON_LABEL[c.cancelReasonCode] || c.cancelReasonCode || '-'
    },
    { label: '原因说明', value: c.cancelReasonDetail || '-', span: 2 },
    { label: '事件日期', value: c.eventDate || '-' },
    {
      label: '业务状态',
      value: CANCELLATION_BUSINESS_STATUS[c.businessStatus] || c.businessStatus || '-'
    },
    { label: '行政区划', value: c.regionCode || '-' },
    { label: '地址', value: c.address || '-', span: 2 },
    { label: '版本号', value: c.version ?? '-' },
    { label: '执行时间', value: c.executedAt || '-' }
  ]
}
