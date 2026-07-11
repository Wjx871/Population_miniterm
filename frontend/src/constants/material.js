export const MATERIAL_VERIFY_STATUS = Object.freeze({
  PENDING: '待核验',
  VERIFIED: '核验通过',
  REJECTED: '核验不通过',
})

export const ACCEPTED_MATERIAL_TYPES = '.pdf,.jpg,.jpeg,.png'
export const MAX_MATERIAL_SIZE = 10 * 1024 * 1024

export const MATERIAL_ACCEPTED_MIME_TYPES = Object.freeze([
  'application/pdf',
  'image/jpeg',
  'image/png',
])

export function getMigrationMaterialOptions(direction, migrationType) {
  const options = direction === 'in'
    ? [
        { value: 'IDENTITY_PROOF', label: '身份证明', required: true },
        { value: 'HOUSEHOLD_BOOK', label: '户口簿（与地址证明二选一）', required: true },
        { value: 'ADDRESS_PROOF', label: '地址证明（与户口簿二选一）', required: true },
      ]
    : [
        { value: 'IDENTITY_PROOF', label: '身份证明', required: true },
        { value: 'HOUSEHOLD_BOOK', label: '户口簿', required: true },
      ]

  if (direction === 'in' && migrationType === 'IN_CITY_CROSS_DISTRICT') {
    options.push({ value: 'MIGRATION_PROOF', label: '迁移证明', required: true })
  }
  return options
}
