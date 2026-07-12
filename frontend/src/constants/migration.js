export const MIGRATION_TYPE_OPTIONS = Object.freeze([
  { value: 'OUTSIDE_CITY', label: '市外迁移' },
  { value: 'IN_CITY_CROSS_DISTRICT', label: '同市跨区迁移' },
])

export const MIGRATION_STATUS = Object.freeze({
  DRAFT: '草稿',
  UNDER_REVIEW: '审批中',
  APPROVED: '已通过，待执行',
  COMPLETED: '已办结',
  REJECTED: '已驳回',
  WITHDRAWN: '已撤回',
})
