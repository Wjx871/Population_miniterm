/**
 * 居住证 ViewModel 适配器
 * 将后端原始数据映射为统一的前端 ViewModel
 */

const pickFirst = (...values) => {
  for (const v of values) {
    if (v !== undefined && v !== null) return v
  }
  return undefined
}

export function normalizeResidencePermit(raw) {
  if (!raw) return null
  return {
    permitId: pickFirst(raw.permitId, raw.id),
    permitNo: raw.permitNo || '',
    personId: raw.personId,
    floatingId: raw.floatingId,
    sourceApplicationId: raw.sourceApplicationId,
    personName: raw.personName || '',
    identityNo: raw.identityNo || '',
    currentAddress: raw.currentAddress || '',
    issueRegionCode: raw.issueRegionCode || '',
    issuingDepartmentId: raw.issuingDepartmentId,
    issuingAuthority: raw.issuingAuthority || '',
    issueDate: raw.issueDate || '',
    validFrom: raw.validFrom || '',
    validUntil: raw.validUntil || '',
    lastEndorsedAt: raw.lastEndorsedAt,
    status: raw.status || '',
    cancellationReason: raw.cancellationReason,
    cancelledAt: raw.cancelledAt,
    currentFlag: raw.currentFlag,
    version: raw.version
  }
}

export function normalizeResidencePermitList(records) {
  if (!Array.isArray(records)) return []
  return records.filter(r => r != null).map(normalizeResidencePermit)
}

/**
 * 居住证申请专业详情 ViewModel
 */
export function normalizePermitProfessional(raw) {
  if (!raw) return null
  const pro = raw.professional || {}
  return {
    application: raw.application,
    professional: {
      permitApplicationId: pickFirst(pro.permitApplicationId, pro.id, pro.applicationId),
      permitId: pickFirst(pro.permitId, pro.executedPermitId),
      permitNo: pickFirst(pro.permitNo, raw.subject?.permitNo),
      floatingId: pickFirst(pro.floatingId, raw.subject?.floatingId),
      personId: pickFirst(pro.personId, raw.subject?.personId),
      personName: pickFirst(pro.personName, raw.subject?.personName),
      identityNo: pickFirst(pro.identityNo, raw.subject?.identityNo, raw.subject?.maskedIdentityNo),
      currentAddress: pickFirst(pro.currentAddress, raw.subject?.currentAddress),
      issueRegionCode: pickFirst(pro.issueRegionCode),
      issuingAuthority: pickFirst(pro.issuingAuthority),
      validFrom: pickFirst(pro.validFrom, raw.subject?.validFrom),
      validUntil: pickFirst(pro.validUntil, raw.subject?.validUntil),
      residenceBasisCode: pickFirst(pro.residenceBasisCode),
      requestedValidFrom: pickFirst(pro.requestedValidFrom),
      requestedValidUntil: pickFirst(pro.requestedValidUntil),
      applyType: pickFirst(pro.applyType),
      businessStatus: pickFirst(pro.businessStatus, pro.status),
      version: pickFirst(pro.version, 0),
      executedPermitId: pickFirst(pro.executedPermitId)
    },
    subject: raw.subject || null,
    materials: raw.materials || [],
    executable: raw.executable,
    unavailableReason: raw.unavailableReason
  }
}

/**
 * 到期提醒专用适配器
 */
export function normalizeExpiringPermit(raw) {
  if (!raw) return null
  return {
    permitId: pickFirst(raw.permitId, raw.id),
    permitNo: raw.permitNo || '',
    personName: raw.personName || '',
    identityNo: raw.maskedIdentityNo || raw.identityNo || '',
    currentAddress: raw.currentAddress || '',
    validUntil: raw.validUntil || '',
    remainingDays: raw.remainingDays,
    status: raw.status || ''
  }
}

export function normalizeExpiringPermitList(records) {
  if (!Array.isArray(records)) return []
  return records.filter(r => r != null).map(normalizeExpiringPermit)
}

/**
 * 构建首次申领居住证的请求体
 */
export function toCreatePermitFirstIssuePayload(form) {
  return {
    floatingId: form.floatingId,
    residenceBasisCode: form.residenceBasisCode,
    title: form.title || '',
    reason: form.reason || '',
    remark: form.remark || '',
    requestedValidFrom: form.requestedValidFrom || null,
    requestedValidUntil: form.requestedValidUntil || null
  }
}

/**
 * 构建签注申请的请求体
 */
export function toCreatePermitEndorsementPayload(form) {
  return {
    residenceBasisCode: form.residenceBasisCode,
    title: form.title || '',
    reason: form.reason || '',
    remark: form.remark || '',
    requestedValidFrom: form.requestedValidFrom || null,
    requestedValidUntil: form.requestedValidUntil || null
  }
}

/**
 * 构建注销申请的请求体
 */
export function toCreatePermitCancellationPayload(form) {
  return {
    title: form.title || '',
    reason: form.reason || '',
    remark: form.remark || ''
  }
}

/**
 * 构建更新居住证草稿的请求体
 */
export function toUpdatePermitApplicationPayload(form, version) {
  return {
    residenceBasisCode: form.residenceBasisCode,
    title: form.title || '',
    reason: form.reason || '',
    remark: form.remark || '',
    requestedValidFrom: form.requestedValidFrom || null,
    requestedValidUntil: form.requestedValidUntil || null,
    version
  }
}
