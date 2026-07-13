export const CANCEL_OBJECT_TYPE = Object.freeze({
  PERSON: 'PERSON',
  HOUSEHOLD: 'HOUSEHOLD'
})

export const CANCEL_OBJECT_TYPE_LABEL = Object.freeze({
  PERSON: '人员注销',
  HOUSEHOLD: '家庭户销户'
})

export const PERSON_CANCELLATION_REASON = Object.freeze({
  DEATH: '死亡',
  DECLARED_DEAD: '宣告死亡',
  SETTLED_ABROAD: '境外定居',
  DUPLICATE_REGISTRATION: '重复登记',
  OTHER_APPROVED: '其他批准注销'
})

export const HOUSEHOLD_CANCELLATION_REASON = Object.freeze({
  NO_ACTIVE_MEMBERS: '无有效成员',
  HOUSEHOLD_MERGED: '整户合并',
  ADDRESS_INVALIDATED: '地址失效',
  OTHER_APPROVED: '其他批准销户'
})

export const CANCELLATION_REASON_LABEL = Object.freeze({
  ...PERSON_CANCELLATION_REASON,
  ...HOUSEHOLD_CANCELLATION_REASON
})

export const CANCELLATION_BUSINESS_STATUS = Object.freeze({
  DRAFT: '草稿',
  UNDER_REVIEW: '审批中',
  APPROVED: '已批准',
  COMPLETED: '已完成',
  REJECTED: '已驳回',
  WITHDRAWN: '已撤回',
  CANCELLED: '已取消'
})

export function getCancellationReasonOptions(objectType) {
  const source = objectType === CANCEL_OBJECT_TYPE.HOUSEHOLD
    ? HOUSEHOLD_CANCELLATION_REASON
    : PERSON_CANCELLATION_REASON
  return Object.entries(source).map(([value, label]) => ({ value, label }))
}

function hasVerified(materials, materialType) {
  return (materials || []).some(
    (item) => item?.materialType === materialType && item?.verifyStatus === 'VERIFIED'
  )
}

/** 按后端 CancellationMaterialRequirementService 规则生成材料选项 */
export function getCancellationMaterialOptions(objectType, reasonCode) {
  if (objectType === CANCEL_OBJECT_TYPE.HOUSEHOLD) {
    return [
      { value: 'CANCELLATION_APPLICATION', label: '注销申请', required: true },
      { value: 'HOUSEHOLD_BOOK', label: '户口簿', required: true },
      { value: 'HOUSEHOLD_CANCELLATION_PROOF', label: '家庭户销户证明', required: false },
      { value: 'HOUSEHOLD_MERGE_PROOF', label: '整户合并证明', required: false }
    ]
  }

  const options = [
    { value: 'APPLICANT_IDENTITY_PROOF', label: '申请人身份证明', required: true }
  ]

  switch (reasonCode) {
    case 'DEATH':
      options.push({ value: 'DEATH_CERTIFICATE', label: '死亡证明', required: true })
      break
    case 'DECLARED_DEAD':
      options.push({ value: 'DECLARED_DEAD_JUDGMENT', label: '宣告死亡文书', required: true })
      break
    case 'SETTLED_ABROAD':
      options.push({ value: 'SETTLEMENT_ABROAD_PROOF', label: '境外定居证明', required: true })
      break
    case 'DUPLICATE_REGISTRATION':
      options.push({ value: 'DUPLICATE_REGISTRATION_PROOF', label: '重复登记证明', required: true })
      break
    default:
      options.push({ value: 'CANCELLATION_APPLICATION', label: '注销申请', required: true })
  }

  if (reasonCode !== 'DUPLICATE_REGISTRATION') {
    options.push({ value: 'HOUSEHOLD_BOOK', label: '户口簿', required: true })
  }

  return options
}

export function getCancellationMaterialRuleText(objectType, reasonCode) {
  if (objectType === CANCEL_OBJECT_TYPE.HOUSEHOLD) {
    return '家庭户销户须核验：注销申请、户口簿，以及销户证明或合并证明之一。'
  }
  const reasonLabel = PERSON_CANCELLATION_REASON[reasonCode] || '对应原因'
  if (reasonCode === 'DUPLICATE_REGISTRATION') {
    return `人员注销（${reasonLabel}）须核验：申请人身份证明、重复登记证明。`
  }
  return `人员注销（${reasonLabel}）须核验：申请人身份证明、原因证明材料、户口簿。`
}

export function hasVerifiedCancellationMaterials(objectType, reasonCode, materials = []) {
  if (objectType === CANCEL_OBJECT_TYPE.HOUSEHOLD) {
    if (!hasVerified(materials, 'CANCELLATION_APPLICATION')) return false
    if (!hasVerified(materials, 'HOUSEHOLD_BOOK')) return false
    return hasVerified(materials, 'HOUSEHOLD_CANCELLATION_PROOF')
      || hasVerified(materials, 'HOUSEHOLD_MERGE_PROOF')
  }

  if (!hasVerified(materials, 'APPLICANT_IDENTITY_PROOF')) return false

  switch (reasonCode) {
    case 'DEATH':
      if (!hasVerified(materials, 'DEATH_CERTIFICATE')) return false
      break
    case 'DECLARED_DEAD':
      if (!hasVerified(materials, 'DECLARED_DEAD_JUDGMENT')) return false
      break
    case 'SETTLED_ABROAD':
      if (!hasVerified(materials, 'SETTLEMENT_ABROAD_PROOF')) return false
      break
    case 'DUPLICATE_REGISTRATION':
      if (!hasVerified(materials, 'DUPLICATE_REGISTRATION_PROOF')) return false
      break
    default:
      if (!hasVerified(materials, 'CANCELLATION_APPLICATION')) return false
  }

  if (reasonCode !== 'DUPLICATE_REGISTRATION' && !hasVerified(materials, 'HOUSEHOLD_BOOK')) {
    return false
  }
  return true
}
