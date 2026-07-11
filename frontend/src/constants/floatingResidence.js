// ==================== 流动人口正式登记状态 ====================
export const FLOATING_STATUS = Object.freeze({
  ACTIVE: '有效登记',
  LEFT: '已离开',
  CANCELLED: '已关闭',
  EXPIRED: '已到期'
})

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
  OTHER_APPROVED: 'OTHER_SUPPORTING_DOCUMENT'
}

// ==================== 动态材料规则 ====================

/**
 * 获取流动登记所需材料选项
 * @param {string} reasonCode - 居住事由
 * @returns {Array<{type: string, label: string, required: boolean}>}
 */
export function getFloatingMaterialOptions(reasonCode) {
  const materials = FLOATING_FIXED_MATERIALS.map(type => ({
    type,
    label: FLOATING_MATERIAL_TYPES[type],
    required: true
  }))
  if (reasonCode && REASON_MATERIAL_MAP[reasonCode]) {
    const type = REASON_MATERIAL_MAP[reasonCode]
    materials.push({ type, label: FLOATING_MATERIAL_TYPES[type], required: true })
  }
  return materials
}

/**
 * 获取居住证申请所需材料选项
 * @param {'FIRST_ISSUE'|'ENDORSEMENT'|'CANCELLATION'} applyType
 * @param {string} reasonCode - 居住事由（首次申领需要）
 * @returns {Array<{type: string, label: string, required: boolean}>}
 */
export function getPermitMaterialOptions(applyType, reasonCode) {
  if (applyType === 'FIRST_ISSUE') {
    const materials = [
      { type: 'APPLICANT_IDENTITY_PROOF', label: PERMIT_MATERIAL_TYPES.APPLICANT_IDENTITY_PROOF, required: true },
      { type: 'PERSON_PHOTO', label: PERMIT_MATERIAL_TYPES.PERSON_PHOTO, required: true },
      { type: 'RESIDENCE_ADDRESS_PROOF', label: PERMIT_MATERIAL_TYPES.RESIDENCE_ADDRESS_PROOF, required: true }
    ]
    if (reasonCode && REASON_MATERIAL_MAP[reasonCode]) {
      const type = REASON_MATERIAL_MAP[reasonCode]
      const label = PERMIT_MATERIAL_TYPES[type] || FLOATING_MATERIAL_TYPES[type] || type
      materials.push({ type, label, required: true })
    }
    return materials
  }
  if (applyType === 'ENDORSEMENT') {
    return [
      { type: 'CURRENT_RESIDENCE_PERMIT', label: PERMIT_MATERIAL_TYPES.CURRENT_RESIDENCE_PERMIT, required: true },
      { type: 'CONTINUED_RESIDENCE_PROOF', label: PERMIT_MATERIAL_TYPES.CONTINUED_RESIDENCE_PROOF, required: true }
    ]
  }
  if (applyType === 'CANCELLATION') {
    return [
      { type: 'CURRENT_RESIDENCE_PERMIT', label: PERMIT_MATERIAL_TYPES.CURRENT_RESIDENCE_PERMIT, required: true },
      { type: 'CANCELLATION_APPLICATION', label: PERMIT_MATERIAL_TYPES.CANCELLATION_APPLICATION, required: true }
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
  return '必需材料：' + requiredTypes.join('、') + '。所有材料须上传并通过核验后方可提交。'
}

export function getPermitMaterialRuleText(applyType, reasonCode) {
  const options = getPermitMaterialOptions(applyType, reasonCode)
  const requiredTypes = options.filter(o => o.required).map(o => o.label)
  return '必需材料：' + requiredTypes.join('、') + '。所有材料须上传并通过核验后方可提交。'
}

// ==================== 材料完整性判断 ====================

/**
 * 检查流动登记材料是否完整（类型存在且VERIFIED）
 * @param {Array} materials - 材料列表
 * @param {string} reasonCode - 居住事由
 * @returns {boolean}
 */
export function hasCompleteFloatingMaterials(materials, reasonCode) {
  const options = getFloatingMaterialOptions(reasonCode)
  const requiredTypes = options.filter(o => o.required).map(o => o.type)
  return requiredTypes.every(type =>
    materials.some(m => m.materialType === type && m.verifyStatus === 'VERIFIED')
  )
}

/**
 * 检查居住证材料是否完整
 * @param {Array} materials - 材料列表
 * @param {'FIRST_ISSUE'|'ENDORSEMENT'|'CANCELLATION'} applyType
 * @param {string} reasonCode - 居住事由（首次申领需要）
 * @returns {boolean}
 */
export function hasCompletePermitMaterials(materials, applyType, reasonCode) {
  const options = getPermitMaterialOptions(applyType, reasonCode)
  const requiredTypes = options.filter(o => o.required).map(o => o.type)
  return requiredTypes.every(type =>
    materials.some(m => m.materialType === type && m.verifyStatus === 'VERIFIED')
  )
}

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
