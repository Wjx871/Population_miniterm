export const MATERIAL_VERIFY_STATUS = Object.freeze({
  PENDING: '待核验',
  VERIFIED: '核验通过',
  REJECTED: '核验不通过',
})

export const MATERIAL_TYPES = Object.freeze([
  { value: 'IDENTITY_PROOF', label: '身份证明' },
  { value: 'HOUSEHOLD_BOOK', label: '户口簿' },
  { value: 'ADDRESS_PROOF', label: '地址证明' },
  { value: 'MIGRATION_PROOF', label: '迁移证明' },
  { value: 'HOUSEHOLD_CONSENT', label: '家庭成员同意材料' },
])

export const ACCEPTED_MATERIAL_TYPES = '.pdf,.jpg,.jpeg,.png'
export const MAX_MATERIAL_SIZE = 10 * 1024 * 1024
