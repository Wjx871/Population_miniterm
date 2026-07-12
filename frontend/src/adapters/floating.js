/**
 * 流动登记 ViewModel 适配器
 * 将后端原始数据映射为统一的前端 ViewModel
 */

const pickFirst = (...values) => {
  for (const v of values) {
    if (v !== undefined && v !== null) return v
  }
  return undefined
}

export function normalizeFloatingPopulation(raw) {
  if (!raw) return null
  return {
    floatingId: pickFirst(raw.floatingId, raw.id),
    registrationNo: raw.registrationNo || '',
    sourceApplicationId: raw.sourceApplicationId,
    personId: raw.personId,
    personName: raw.personName || '',
    identityNo: raw.identityNo || '',
    phone: raw.phone || '',
    sourceRegionCode: raw.sourceRegionCode || '',
    sourceAddress: raw.sourceAddress || '',
    currentRegionCode: raw.currentRegionCode || '',
    currentAddress: raw.currentAddress || '',
    residenceReasonCode: raw.residenceReasonCode || '',
    residenceProofType: raw.residenceProofType || '',
    arrivalDate: raw.arrivalDate || '',
    plannedLeaveDate: raw.plannedLeaveDate || '',
    registrationDate: raw.registrationDate || '',
    eligibleFromDate: raw.eligibleFromDate || '',
    departmentId: raw.departmentId,
    operatorId: raw.operatorId,
    status: raw.status || '',
    closeReasonCode: raw.closeReasonCode,
    closedAt: raw.closedAt,
    currentFlag: raw.currentFlag,
    version: raw.version
  }
}

export function normalizeFloatingList(records) {
  if (!Array.isArray(records)) return []
  return records.filter(r => r != null).map(normalizeFloatingPopulation)
}

/**
 * 流动登记申请专业详情 ViewModel
 */
export function normalizeFloatingProfessional(raw) {
  if (!raw) return null
  const pro = raw.professional || {}
  return {
    application: raw.application,
    professional: {
      floatingApplicationId: pickFirst(pro.floatingApplicationId, pro.id, pro.applicationId),
      floatingId: pickFirst(pro.floatingId, pro.executedFloatingId),
      registrationNo: pickFirst(pro.registrationNo),
      personId: pickFirst(pro.personId),
      personName: pickFirst(pro.personName, raw.subject?.personName),
      identityNo: pickFirst(pro.identityNo, raw.subject?.identityNo, raw.subject?.maskedIdentityNo),
      phone: pickFirst(pro.phone, pro.applicantPhone, raw.subject?.phone),
      sourceRegionCode: pickFirst(pro.sourceRegionCode),
      sourceAddress: pickFirst(pro.sourceAddress),
      currentRegionCode: pickFirst(pro.currentRegionCode),
      currentAddress: pickFirst(pro.currentAddress),
      residenceReasonCode: pickFirst(pro.residenceReasonCode, raw.subject?.residenceReasonCode),
      residenceProofType: pickFirst(pro.residenceProofType),
      arrivalDate: pickFirst(pro.arrivalDate),
      plannedLeaveDate: pickFirst(pro.plannedLeaveDate),
      applicantPhone: pickFirst(pro.applicantPhone, pro.phone),
      businessStatus: pickFirst(pro.businessStatus, pro.status),
      version: pro.version,
      executedFloatingId: pickFirst(pro.executedFloatingId)
    },
    subject: raw.subject || null,
    materials: raw.materials || [],
    executable: raw.executable,
    unavailableReason: raw.unavailableReason
  }
}

/**
 * 构建创建流动登记申请的请求体
 */
export function toCreateFloatingApplicationPayload(form) {
  return {
    personId: form.personId,
    sourceRegionCode: form.sourceRegionCode,
    sourceAddress: form.sourceAddress || '',
    currentRegionCode: form.currentRegionCode,
    currentAddress: form.currentAddress,
    residenceReasonCode: form.residenceReasonCode,
    residenceProofType: form.residenceProofType,
    arrivalDate: form.arrivalDate,
    plannedLeaveDate: form.plannedLeaveDate || null,
    applicantPhone: form.applicantPhone || '',
    title: form.title || '',
    reason: form.reason || '',
    remark: form.remark || ''
  }
}

/**
 * 构建更新流动登记草稿的请求体
 */
export function toUpdateFloatingApplicationPayload(form, version) {
  return {
    personId: form.personId,
    sourceRegionCode: form.sourceRegionCode,
    sourceAddress: form.sourceAddress || '',
    currentRegionCode: form.currentRegionCode,
    currentAddress: form.currentAddress,
    residenceReasonCode: form.residenceReasonCode,
    residenceProofType: form.residenceProofType,
    arrivalDate: form.arrivalDate,
    plannedLeaveDate: form.plannedLeaveDate || null,
    applicantPhone: form.applicantPhone || '',
    title: form.title || '',
    reason: form.reason || '',
    remark: form.remark || '',
    version
  }
}
