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
        { value: 'HOUSEHOLD_BOOK', label: '户口簿', required: true },
        { value: 'ADDRESS_PROOF', label: '地址证明', required: true },
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

export function getMigrationMaterialRuleText(direction, migrationType) {
  if (direction === 'out') return '迁出须核验通过：身份证明、户口簿。'
  return migrationType === 'IN_CITY_CROSS_DISTRICT'
    ? '迁入须核验通过：身份证明、户口簿或地址证明至少一项、迁移证明。'
    : '迁入须核验通过：身份证明、户口簿或地址证明至少一项。'
}

function hasVerifiedMaterial(materials, materialType) {
  return materials.some((item) => item?.materialType === materialType && item?.verifyStatus === 'VERIFIED')
}

export function hasCompleteMigrationMaterials(direction, migrationType, materials = []) {
  if (!hasVerifiedMaterial(materials, 'IDENTITY_PROOF')) return false
  if (direction === 'out') return hasVerifiedMaterial(materials, 'HOUSEHOLD_BOOK')
  const hasResidenceProof = hasVerifiedMaterial(materials, 'HOUSEHOLD_BOOK') || hasVerifiedMaterial(materials, 'ADDRESS_PROOF')
  if (!hasResidenceProof) return false
  return migrationType !== 'IN_CITY_CROSS_DISTRICT' || hasVerifiedMaterial(materials, 'MIGRATION_PROOF')
}
