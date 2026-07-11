export const APPLICATION_STATUS = Object.freeze({
  DRAFT: '草稿',
  SUBMITTED: '已提交',
  UNDER_REVIEW: '审批中',
  APPROVED: '已通过，待执行',
  REJECTED: '已驳回',
  WITHDRAWN: '已撤回',
  COMPLETED: '已办结',
  CANCELLED: '已取消',
})

export const BUSINESS_TYPE = Object.freeze({
  MIGRATION_IN: 'MIGRATION_IN',
  MIGRATION_OUT: 'MIGRATION_OUT',
})

export const BUSINESS_TYPE_LABEL = Object.freeze({
  [BUSINESS_TYPE.MIGRATION_IN]: '迁入申请',
  [BUSINESS_TYPE.MIGRATION_OUT]: '迁出申请',
})

export const READONLY_APPLICATION_STATUS = new Set([
  'SUBMITTED', 'UNDER_REVIEW', 'APPROVED', 'REJECTED', 'WITHDRAWN', 'COMPLETED', 'CANCELLED',
])
