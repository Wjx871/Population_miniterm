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

function trimOrNull(value) {
  const text = trimOrEmpty(value)
  return text || null
}

/**
 * 将后端 Person 转为前端统一 ViewModel。
 */
export function normalizePerson(raw) {
  if (!raw || typeof raw !== 'object') {
    return {
      id: null,
      name: '',
      gender: '',
      idCard: '',
      birthDate: null,
      ethnicity: '',
      phone: '',
      currentAddress: '',
      status: '',
      createdAt: null,
      updatedAt: null,
      hasCurrentResidence: null,
      floatingFlag: null,
      keyPopulationFlag: null,
    }
  }

  return {
    id: pickFirst(raw.personId, raw.id),
    name: trimOrEmpty(raw.name),
    gender: trimOrEmpty(raw.gender),
    idCard: trimOrEmpty(raw.idCard),
    birthDate: raw.birthDate ?? null,
    ethnicity: trimOrEmpty(raw.ethnicity),
    phone: trimOrEmpty(raw.phone),
    currentAddress: trimOrEmpty(raw.currentAddress),
    status: trimOrEmpty(raw.status),
    createdAt: raw.createdAt ?? null,
    updatedAt: raw.updatedAt ?? null,
    // 后端未提供的关联字段，保持 null，禁止伪造业务真值
    hasCurrentResidence: raw.hasCurrentResidence ?? null,
    floatingFlag: raw.floatingFlag ?? null,
    keyPopulationFlag: raw.keyPopulationFlag ?? null,
  }
}

export function normalizePersonList(records) {
  if (!Array.isArray(records)) return []
  return records.map((item) => normalizePerson(item))
}

/**
 * 新增人口 payload。
 * status 固定为「正常」，不在基础表单暴露状态选择。
 * idCardImageId 由上层（ImageScannerDialog）注入，值为 null 时由后端拒绝。
 */
export function toCreatePersonPayload(form = {}) {
  const imageId = form.idCardImageId
  return {
    name: trimOrEmpty(form.name),
    gender: trimOrEmpty(form.gender),
    idCard: trimOrEmpty(form.idCard).toUpperCase(),
    birthDate: toDatePayload(form.birthDate),
    ethnicity: trimOrNull(form.ethnicity),
    phone: trimOrEmpty(form.phone) || null,
    currentAddress: trimOrNull(form.currentAddress),
    status: '正常',
    idCardImageId: imageId ?? null,
  }
}

/**
 * 更新人口 payload。
 * idCard / status 必须由调用方传入「最新详情」中的值，禁止依赖可被篡改的隐藏表单字段。
 *
 * @param {object} form 可编辑字段（姓名、性别、出生日期等）
 * @param {object} latestDetail normalize 后的最新详情，至少含 idCard、status
 */
export function toUpdatePersonPayload(form = {}, latestDetail = {}) {
  const detail = normalizePerson(latestDetail)
  const status = trimOrEmpty(detail.status)
  // 更新时禁止在 status 缺失时兜底为「正常」，避免误覆盖「已注销」等状态
  if (!status) {
    throw new Error('最新详情缺少档案状态，无法提交更新')
  }

  const idCard = trimOrEmpty(detail.idCard).toUpperCase()
  if (!idCard) {
    throw new Error('最新详情缺少身份证号，无法提交更新')
  }

  return {
    name: trimOrEmpty(form.name),
    gender: trimOrEmpty(form.gender),
    // 身份证与状态取自最新详情，避免前端隐藏字段被改写后误覆盖
    idCard,
    birthDate: toDatePayload(form.birthDate),
    ethnicity: trimOrNull(form.ethnicity),
    phone: trimOrEmpty(form.phone) || null,
    currentAddress: trimOrNull(form.currentAddress),
    status,
  }
}
