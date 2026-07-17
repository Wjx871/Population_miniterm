// ==================== 流动人口正式登记状态 ====================
export const FLOATING_STATUS = Object.freeze({
  ACTIVE: '有效登记',
  LEFT: '已离开',
  CANCELLED: '已关闭',
  EXPIRED: '已到期'
})

// 与后端 FloatingResidenceService.closeFloating 中的 reasonCode 取值保持一致
export const CLOSE_REASON_CODE = Object.freeze({
  LEFT_REGION: 'LEFT_REGION',
  REGISTERED_LOCAL_HOUSEHOLD: 'REGISTERED_LOCAL_HOUSEHOLD',
  PERSON_CANCELLED: 'PERSON_CANCELLED',
  DUPLICATE_RECORD: 'DUPLICATE_RECORD',
  OTHER_APPROVED: 'OTHER_APPROVED'
})

/**
 * 流动登记的"非当前"状态集合。
 * - 列表默认只看当前在册；
 * - 历史行（current_flag 为空）如果通过 includeHistory=true 返回，将按这里的状态给出友好标签。
 */
export const FLOATING_INACTIVE_STATUSES = Object.freeze(['LEFT', 'CANCELLED', 'EXPIRED'])

export function isFloatingClosed(status) {
  return FLOATING_INACTIVE_STATUSES.includes(status)
}

// ==================== 居住证正式证件状态 ====================
export const RESIDENCE_PERMIT_STATUS = Object.freeze({
  ACTIVE: '有效',
  CANCELLED: '已注销',
  EXPIRED: '已过期'
})

// ==================== 居住事由 ====================
export const RESIDENCE_REASON = Object.freeze({
  EMPLOYMENT: '就业',
  STUDY: '学习',
  FAMILY: '家庭团聚',
  FAMILY_REUNION: '家庭团聚',
  BUSINESS: '经商',
  OTHER_APPROVED: '其他'
})

// ==================== 居住证明类型 ====================
export const RESIDENCE_PROOF_TYPE = Object.freeze({
  RENTAL_CONTRACT: '租赁合同',
  PROPERTY_CERTIFICATE: '房产证明',
  EMPLOYER_ACCOMMODATION: '单位宿舍',
  SCHOOL_ACCOMMODATION: '学校宿舍',
  HOST_CONFIRMATION: '寄宿确认',
  OTHER_APPROVED: '其他'
})

// ==================== 关闭原因 ====================
export const CLOSE_REASON = Object.freeze({
  LEFT_REGION: '离开本区',
  REGISTERED_LOCAL_HOUSEHOLD: '已落户本地',
  PERSON_CANCELLED: '人员注销',
  DUPLICATE_RECORD: '重复记录',
  OTHER_APPROVED: '其他'
})

// ==================== 居住依据 ====================
export const RESIDENCE_BASIS = Object.freeze({
  STABLE_EMPLOYMENT: '稳定就业',
  STABLE_RESIDENCE: '稳定住所',
  CONTINUOUS_STUDY: '连续就读'
})

// ==================== 居住证申请类型 ====================
export const PERMIT_APPLY_TYPE = Object.freeze({
  FIRST_ISSUE: '首次申领',
  ENDORSEMENT: '签注',
  CANCELLATION: '注销'
})

// ==================== 材料类型常量 ====================
export const FLOATING_MATERIAL_TYPES = Object.freeze({
  APPLICANT_IDENTITY_PROOF: '申请人身份证明',
  RESIDENCE_ADDRESS_PROOF: '居住地址证明',
  EMPLOYMENT_PROOF: '就业证明',
  STUDY_PROOF: '在读证明',
  BUSINESS_PROOF: '经营证明',
  FAMILY_RELATIONSHIP_PROOF: '亲属关系证明',
  OTHER_SUPPORTING_DOCUMENT: '其他辅助材料'
})

export const PERMIT_MATERIAL_TYPES = Object.freeze({
  APPLICANT_IDENTITY_PROOF: '申请人身份证明',
  PERSON_PHOTO: '申请人照片',
  RESIDENCE_ADDRESS_PROOF: '居住地址证明',
  EMPLOYMENT_PROOF: '就业证明',
  STUDY_PROOF: '在读证明',
  BUSINESS_PROOF: '经营证明',
  FAMILY_RELATIONSHIP_PROOF: '亲属关系证明',
  OTHER_SUPPORTING_DOCUMENT: '其他辅助材料',
  CURRENT_RESIDENCE_PERMIT: '当前居住证',
  CONTINUED_RESIDENCE_PROOF: '持续居住证明',
  CANCELLATION_APPLICATION: '注销申请表'
})

// ==================== 固定必需材料 ====================
const FLOATING_FIXED_MATERIALS = ['APPLICANT_IDENTITY_PROOF', 'RESIDENCE_ADDRESS_PROOF']

// 流动登记按事由的额外材料
const REASON_MATERIAL_MAP = {
  EMPLOYMENT: 'EMPLOYMENT_PROOF',
  STUDY: 'STUDY_PROOF',
  BUSINESS: 'BUSINESS_PROOF',
  FAMILY: 'FAMILY_RELATIONSHIP_PROOF',
  FAMILY_REUNION: 'FAMILY_RELATIONSHIP_PROOF',
  OTHER_APPROVED: 'OTHER_SUPPORTING_DOCUMENT'
}

// ==================== 动态材料规则 ====================

/**
 * 获取流动登记所需材料选项
 * @param {string} reasonCode - 居住事由
 * @returns {Array<{value: string, label: string, required: boolean}>}
 */
export function getFloatingMaterialOptions(reasonCode) {
  const materials = FLOATING_FIXED_MATERIALS.map(type => ({
    value: type,
    label: FLOATING_MATERIAL_TYPES[type],
    required: true
  }))
  if (reasonCode && REASON_MATERIAL_MAP[reasonCode]) {
    const type = REASON_MATERIAL_MAP[reasonCode]
    materials.push({ value: type, label: FLOATING_MATERIAL_TYPES[type], required: true })
  }
  return materials
}

/**
 * 获取居住证申请所需材料选项
 * @param {'FIRST_ISSUE'|'ENDORSEMENT'|'CANCELLATION'} applyType
 * @param {string} reasonCode - 居住事由（首次申领需要）
 * @returns {Array<{value: string, label: string, required: boolean}>}
 */
export function getPermitMaterialOptions(applyType, reasonCode) {
  if (applyType === 'FIRST_ISSUE') {
    const materials = [
      { value: 'APPLICANT_IDENTITY_PROOF', label: PERMIT_MATERIAL_TYPES.APPLICANT_IDENTITY_PROOF, required: true },
      { value: 'PERSON_PHOTO', label: PERMIT_MATERIAL_TYPES.PERSON_PHOTO, required: true },
      { value: 'RESIDENCE_ADDRESS_PROOF', label: PERMIT_MATERIAL_TYPES.RESIDENCE_ADDRESS_PROOF, required: true }
    ]
    if (reasonCode && REASON_MATERIAL_MAP[reasonCode]) {
      const type = REASON_MATERIAL_MAP[reasonCode]
      const label = PERMIT_MATERIAL_TYPES[type] || FLOATING_MATERIAL_TYPES[type] || type
      materials.push({ value: type, label, required: true })
    }
    return materials
  }
  if (applyType === 'ENDORSEMENT') {
    return [
      { value: 'CURRENT_RESIDENCE_PERMIT', label: PERMIT_MATERIAL_TYPES.CURRENT_RESIDENCE_PERMIT, required: true },
      { value: 'CONTINUED_RESIDENCE_PROOF', label: PERMIT_MATERIAL_TYPES.CONTINUED_RESIDENCE_PROOF, required: true }
    ]
  }
  if (applyType === 'CANCELLATION') {
    return [
      { value: 'CURRENT_RESIDENCE_PERMIT', label: PERMIT_MATERIAL_TYPES.CURRENT_RESIDENCE_PERMIT, required: true },
      { value: 'CANCELLATION_APPLICATION', label: PERMIT_MATERIAL_TYPES.CANCELLATION_APPLICATION, required: true }
    ]
  }
  return []
}

// ==================== 材料规则文案 ====================

export function getFloatingMaterialRuleText(reasonCode) {
  const requiredTypes = FLOATING_FIXED_MATERIALS.map(t => FLOATING_MATERIAL_TYPES[t])
  if (reasonCode && REASON_MATERIAL_MAP[reasonCode]) {
    requiredTypes.push(FLOATING_MATERIAL_TYPES[REASON_MATERIAL_MAP[reasonCode]] || REASON_MATERIAL_MAP[reasonCode])
  }
  return '必需材料：' + requiredTypes.join('、') + '。提交前须上传全部必需材料；材料将在审批阶段进行核验。'
}

export function getPermitMaterialRuleText(applyType, reasonCode) {
  const options = getPermitMaterialOptions(applyType, reasonCode)
  const requiredTypes = options.filter(o => o.required).map(o => o.label)
  return '必需材料：' + requiredTypes.join('、') + '。提交前须上传全部必需材料；材料将在审批阶段进行核验。'
}

// ==================== 材料完整性判断 ====================

/**
 * 检查流动登记必需材料是否已上传（不要求VERIFIED，用于提交按钮）
 */
export function hasUploadedFloatingMaterials(materials, reasonCode) {
  const options = getFloatingMaterialOptions(reasonCode)
  const requiredValues = options.filter(o => o.required).map(o => o.value)
  return requiredValues.every(value =>
    materials.some(m => m.materialType === value)
  )
}

/**
 * 检查流动登记必需材料是否全部核验通过（要求VERIFIED，用于审批按钮）
 */
export function hasVerifiedFloatingMaterials(materials, reasonCode) {
  const options = getFloatingMaterialOptions(reasonCode)
  const requiredValues = options.filter(o => o.required).map(o => o.value)
  return requiredValues.every(value =>
    materials.some(m => m.materialType === value && m.verifyStatus === 'VERIFIED')
  )
}

/**
 * 检查居住证必需材料是否已上传（不要求VERIFIED，用于提交按钮）
 */
export function hasUploadedPermitMaterials(materials, applyType, reasonCode) {
  const options = getPermitMaterialOptions(applyType, reasonCode)
  const requiredValues = options.filter(o => o.required).map(o => o.value)
  return requiredValues.every(value =>
    materials.some(m => m.materialType === value)
  )
}

/**
 * 检查居住证必需材料是否全部核验通过（要求VERIFIED，用于审批按钮）
 */
export function hasVerifiedPermitMaterials(materials, applyType, reasonCode) {
  const options = getPermitMaterialOptions(applyType, reasonCode)
  const requiredValues = options.filter(o => o.required).map(o => o.value)
  return requiredValues.every(value =>
    materials.some(m => m.materialType === value && m.verifyStatus === 'VERIFIED')
  )
}

// 保留旧函数名以兼容（标记为deprecated）

// ==================== 生命周期动作 ====================
export const LIFECYCLE_ACTION = Object.freeze({
  ISSUE: '首次签发',
  ENDORSE: '签注',
  CANCEL: '注销',
  EXPIRE: '自动到期',
  REGISTRATION_CLOSED: '登记关闭联动注销'
})

// ==================== 居住证申请 businessType 常量 ====================
export const PERMIT_BUSINESS_TYPE = Object.freeze({
  FIRST_ISSUE: 'RESIDENCE_PERMIT_FIRST_ISSUE',
  ENDORSEMENT: 'RESIDENCE_PERMIT_ENDORSEMENT',
  CANCELLATION: 'RESIDENCE_PERMIT_CANCELLATION'
})

// ==================== 执行类型 ====================
export const EXECUTE_TYPE = Object.freeze({
  FLOATING_EXECUTE: 'FLOATING_EXECUTE',
  PERMIT_ISSUE: 'PERMIT_ISSUE',
  PERMIT_ENDORSE: 'PERMIT_ENDORSE',
  PERMIT_CANCEL: 'PERMIT_CANCEL'
})

// ==================== 动态路由生成 ====================
export function getPermitApplyRoute(floatingId) {
  return `/residence-permits/first-issue?floatingId=${floatingId}`
}

export function getEndorsementApplyRoute(permitId) {
  return `/residence-permits/${permitId}/endorsement/apply`
}

export function getCancellationApplyRoute(permitId) {
  return `/residence-permits/${permitId}/cancellation/apply`
}
