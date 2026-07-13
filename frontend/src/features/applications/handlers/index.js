import { createMigrationHandler } from './migrationHandler'
import { createFloatingHandler } from './floatingHandler'
import { createResidencePermitHandler } from './residencePermitHandler'

import { getMigrationApplicationDetail, executeMigrationIn, executeMigrationOut } from '../../../api/migrations'
import {
  getFloatingApplicationDetail,
  executeFloatingApplication,
  getPermitApplicationDetail,
  issueResidencePermit,
  endorseResidencePermit,
  cancelResidencePermitApplication
} from '../../../api/floatingResidence'
import {
  getMigrationMaterialOptions,
  getMigrationMaterialRuleText,
  hasCompleteMigrationMaterials
} from '../../../constants/material'
import {
  getFloatingMaterialOptions,
  getFloatingMaterialRuleText,
  getPermitMaterialOptions,
  getPermitMaterialRuleText,
  hasVerifiedFloatingMaterials,
  hasVerifiedPermitMaterials
} from '../../../constants/floatingResidence'
import { normalizeFloatingProfessional } from '../../../adapters/floating'
import { normalizePermitProfessional } from '../../../adapters/residencePermit'

const migrationHandler = createMigrationHandler({
  getMigrationApplicationDetail,
  executeMigrationIn,
  executeMigrationOut,
  getMigrationMaterialOptions,
  getMigrationMaterialRuleText,
  hasCompleteMigrationMaterials
})

const floatingHandler = createFloatingHandler({
  getFloatingApplicationDetail,
  executeFloatingApplication,
  getFloatingMaterialOptions,
  getFloatingMaterialRuleText,
  hasVerifiedFloatingMaterials,
  normalizeFloatingProfessional
})

const residencePermitHandler = createResidencePermitHandler({
  getPermitApplicationDetail,
  issueResidencePermit,
  endorseResidencePermit,
  cancelResidencePermitApplication,
  getPermitMaterialOptions,
  getPermitMaterialRuleText,
  hasVerifiedPermitMaterials,
  normalizePermitProfessional
})

const handlers = [migrationHandler, floatingHandler, residencePermitHandler]

export function getApplicationBusinessHandler(businessType) {
  for (const handler of handlers) {
    if (handler.supports(businessType)) {
      return handler
    }
  }
  return null
}

/** 供后续检查点注册新业务 Handler（避免重复两套分发） */
export function registerApplicationBusinessHandler(handler) {
  if (!handler || typeof handler.supports !== 'function') {
    throw new Error('Invalid application business handler')
  }
  handlers.push(handler)
}
