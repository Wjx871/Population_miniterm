import { toDatePayload } from '../utils/date'

function pickFirst(...values) {
  for (const value of values) {
    if (value !== undefined && value !== null && value !== '') {
      return value
    }
  }
  return null
}

function trimOrEmpty(value) {
  if (value === undefined || value === null) return ''
  return String(value).trim()
}

function toNumberOrZero(value) {
  const number = Number(value)
  return Number.isFinite(number) ? number : 0
}

/**
 * 家庭户 ViewModel。
 * 接口契约待后端确认；字段映射兼容常见命名。
 */
export function normalizeHousehold(raw) {
  if (!raw || typeof raw !== 'object') {
    return {
      id: null,
      householdNo: '',
      headPersonId: null,
      headPersonName: '',
      address: '',
      householdType: null,
      status: '',
      establishDate: null,
      memberCount: 0,
      regionName: null,
      departmentName: null,
    }
  }

  return {
    id: pickFirst(raw.householdId, raw.id),
    householdNo: trimOrEmpty(raw.householdNo),
    headPersonId: pickFirst(raw.headPersonId, raw.headId),
    headPersonName: trimOrEmpty(raw.headPersonName || raw.headName),
    address: trimOrEmpty(raw.address),
    householdType: raw.householdType ?? null,
    status: trimOrEmpty(raw.status),
    establishDate: raw.establishDate ?? null,
    memberCount: toNumberOrZero(raw.memberCount),
    regionName: raw.regionName ?? null,
    departmentName: raw.departmentName ?? null,
  }
}

export function normalizeHouseholdList(records) {
  if (!Array.isArray(records)) return []
  return records.map((item) => normalizeHousehold(item))
}

/**
 * 成员 ViewModel。
 * isHead 优先使用后端标记，其次与户主 personId 比较。
 */
export function normalizeHouseholdMember(raw, headPersonId = null) {
  if (!raw || typeof raw !== 'object') {
    return {
      memberId: null,
      personId: null,
      personName: '',
      idCard: '',
      phone: '',
      relationship: '',
      joinDate: null,
      isHead: false,
      status: '',
    }
  }

  const personId = pickFirst(raw.personId, raw.person?.personId, raw.person?.id)
  let isHead = false
  if (typeof raw.isHead === 'boolean') {
    isHead = raw.isHead
  } else if (headPersonId != null && personId != null) {
    isHead = String(personId) === String(headPersonId)
  } else if (trimOrEmpty(raw.relationship) === '户主') {
    isHead = true
  }

  return {
    memberId: pickFirst(raw.memberId, raw.id),
    personId,
    personName: trimOrEmpty(raw.personName || raw.name || raw.person?.name),
    idCard: trimOrEmpty(raw.idCard || raw.personIdCard || raw.person?.idCard),
    phone: trimOrEmpty(raw.phone || raw.person?.phone),
    relationship: trimOrEmpty(raw.relationship),
    joinDate: raw.joinDate ?? null,
    isHead,
    status: trimOrEmpty(raw.status),
  }
}

export function normalizeHouseholdMembers(records, headPersonId = null) {
  if (!Array.isArray(records)) return []
  return records.map((item) => normalizeHouseholdMember(item, headPersonId))
}

export function toCreateHouseholdPayload(form = {}) {
  return {
    headPersonId: form.headPersonId ?? null,
    address: trimOrEmpty(form.address),
    establishDate: toDatePayload(form.establishDate),
  }
}

/**
 * 基础信息编辑：不含户主、状态、成员列表。
 */
export function toUpdateHouseholdPayload(form = {}) {
  return {
    address: trimOrEmpty(form.address),
    establishDate: toDatePayload(form.establishDate),
  }
}
