export const ATTENTION_LEVEL = Object.freeze({
  LOW: '低',
  MEDIUM: '中',
  HIGH: '高'
})

export const KEY_POPULATION_STATUS = Object.freeze({
  ACTIVE: '有效',
  RELEASED: '已解除'
})

export const KEY_HISTORY_EVENT = Object.freeze({
  REGISTERED: '建档',
  RELEASED: '解除'
})

export function getKeyPopulationMaterialOptions() {
  return [
    { value: 'KEY_POPULATION_BASIS', label: '建档依据', required: true },
    { value: 'SITUATION_DESCRIPTION', label: '情况说明', required: true }
  ]
}

export function getKeyPopulationMaterialRuleText() {
  return '提交前须上传：建档依据、情况说明。材料将在审批阶段由审批人员核验。'
}

export function hasVerifiedKeyPopulationMaterials(materials = []) {
  const verified = (type) => materials.some(
    (m) => m?.materialType === type && m?.verifyStatus === 'VERIFIED'
  )
  return verified('KEY_POPULATION_BASIS') && verified('SITUATION_DESCRIPTION')
}
