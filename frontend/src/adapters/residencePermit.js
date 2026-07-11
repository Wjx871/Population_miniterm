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
  return {
    application: raw.application,
    professional: {
      permitId: pickFirst(raw.professional?.permitId, raw.permitId),
      permitNo: pickFirst(raw.professional?.permitNo, raw.permitNo),
      floatingId: pickFirst(raw.professional?.floatingId, raw.floatingId),
      personId: pickFirst(raw.professional?.personId, raw.personId),
      personName: pickFirst(raw.professional?.personName, raw.personName),
      identityNo: pickFirst(raw.professional?.identityNo, raw.identityNo),
      currentAddress: pickFirst(raw.professional?.currentAddress, raw.currentAddress),
      issueRegionCode: pickFirst(raw.professional?.issueRegionCode, raw.issueRegionCode),
      issuingAuthority: pickFirst(raw.professional?.issuingAuthority, raw.issuingAuthority),
      validFrom: pickFirst(raw.professional?.validFrom, raw.validFrom),
      validUntil: pickFirst(raw.professional?.validUntil, raw.validUntil),
      residenceBasisCode: pickFirst(raw.professional?.residenceBasisCode, raw.residenceBasisCode),
      requestedValidFrom: pickFirst(raw.professional?.requestedValidFrom, raw.requestedValidFrom),
      requestedValidUntil: pickFirst(raw.professional?.requestedValidUntil, raw.requestedValidUntil),
      applyType: pickFirst(raw.professional?.applyType, raw.applyType),
      version: pickFirst(raw.professional?.version, raw.version)
    },
    subject: raw.subject,
    materials: raw.materials || [],
    executable: raw.executable,
    unavailableReason: raw.unavailableReason
  }
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
