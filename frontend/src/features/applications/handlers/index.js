import { createMigrationHandler } from './migrationHandler'
import { createFloatingHandler } from './floatingHandler'
import { createResidencePermitHandler } from './residencePermitHandler'
import { createCancellationHandler } from './cancellationHandler'
import { createExportHandler } from './exportHandler'
import { createKeyPopulationHandler } from './keyPopulationHandler'

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
  getCancellationApplicationDetail,
  executePersonCancellation,
  executeHouseholdCancellation
} from '../../../api/cancellations'
import {
  getExportApplicationDetail,
  executeSensitiveExport
} from '../../../api/exports'
import {
  getRegisterApplicationDetail,
  getReleaseApplicationDetail,
  submitRegisterApplication,
  submitReleaseApplication,
  executeRegisterApplication,
  executeReleaseApplication
} from '../../../api/keyPopulation'
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
import {
  getCancellationMaterialOptions,
  getCancellationMaterialRuleText,
  hasVerifiedCancellationMaterials
} from '../../../constants/cancellation'
import { normalizeFloatingProfessional } from '../../../adapters/floating'
import { normalizePermitProfessional } from '../../../adapters/residencePermit'
import { normalizeCancellationProfessional } from '../../../adapters/cancellation'
import { normalizeExportApplication } from '../../../adapters/export'
import {
  getSensitiveExportMaterialOptions,
  getSensitiveExportMaterialRuleText,
  hasVerifiedSensitiveExportMaterials
} from '../../../constants/export'
import { normalizeKeyPopulationApplication } from '../../../adapters/keyPopulation'
import {
  getKeyPopulationMaterialOptions,
  getKeyPopulationMaterialRuleText,
  hasVerifiedKeyPopulationMaterials
} from '../../../constants/keyPopulation'

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

const cancellationHandler = createCancellationHandler({
  getCancellationApplicationDetail,
  executePersonCancellation,
  executeHouseholdCancellation,
  getCancellationMaterialOptions,
  getCancellationMaterialRuleText,
  hasVerifiedCancellationMaterials,
  normalizeCancellationProfessional
})

const exportHandler = createExportHandler({
  getExportApplicationDetail,
  executeSensitiveExport,
  normalizeExportApplication,
  getSensitiveExportMaterialOptions,
  getSensitiveExportMaterialRuleText,
  hasVerifiedSensitiveExportMaterials
})

const keyPopulationHandler = createKeyPopulationHandler({
  getRegisterApplicationDetail,
  getReleaseApplicationDetail,
  submitRegisterApplication,
  submitReleaseApplication,
  executeRegisterApplication,
  executeReleaseApplication,
  normalizeKeyPopulationApplication,
  getKeyPopulationMaterialOptions,
  getKeyPopulationMaterialRuleText,
  hasVerifiedKeyPopulationMaterials
})

const handlers = [
  migrationHandler,
  floatingHandler,
  residencePermitHandler,
  cancellationHandler,
  exportHandler,
  keyPopulationHandler
]

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
