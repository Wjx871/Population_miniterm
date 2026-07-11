import { createMigrationHandler } from './migrationHandler'
import { createFloatingHandler } from './floatingHandler'
import { createResidencePermitHandler } from './residencePermitHandler'

import { getMigrationApplicationDetail, executeMigrationIn, executeMigrationOut } from '../../../api/migrations'
import { getFloatingApplicationDetail, executeFloatingApplication, getPermitApplicationDetail, issueResidencePermit, endorseResidencePermit, cancelResidencePermitApplication } from '../../../api/floatingResidence'
import { getMigrationMaterialOptions, getMigrationMaterialRuleText, hasCompleteMigrationMaterials } from '../../../constants/material'
import { getFloatingMaterialOptions, getFloatingMaterialRuleText, getPermitMaterialOptions, getPermitMaterialRuleText, hasVerifiedFloatingMaterials, hasVerifiedPermitMaterials } from '../../../constants/floatingResidence'

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
  hasVerifiedFloatingMaterials
})

const residencePermitHandler = createResidencePermitHandler({
  getPermitApplicationDetail,
  issueResidencePermit,
  endorseResidencePermit,
  cancelResidencePermitApplication,
  getPermitMaterialOptions,
  getPermitMaterialRuleText,
  hasVerifiedPermitMaterials
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
