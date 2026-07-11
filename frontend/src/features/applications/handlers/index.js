import { createMigrationHandler } from './migrationHandler'
import { createFloatingHandler } from './floatingHandler'
import { createResidencePermitHandler } from './residencePermitHandler'

import { getMigrationApplicationDetail, executeMigrationIn, executeMigrationOut } from '../../../api/migrations'
import { getFloatingApplicationDetail, executeFloatingApplication, getPermitApplicationDetail, issueResidencePermit, endorseResidencePermit, cancelResidencePermitApplication } from '../../../api/floatingResidence'
import { getMigrationMaterialOptions, hasCompleteMigrationMaterials } from '../../../constants/material'
import { getFloatingMaterialOptions, getPermitMaterialOptions, hasVerifiedFloatingMaterials, hasVerifiedPermitMaterials } from '../../../constants/floatingResidence'

export function getApplicationBusinessHandler(businessType) {
  const migrationHandler = createMigrationHandler({
    getMigrationApplicationDetail,
    executeMigrationIn,
    executeMigrationOut,
    getMigrationMaterialOptions,
    hasCompleteMigrationMaterials: ({ businessType, detail, materials }) => {
      // 这里的 detail 就是 loadDetail 返回的原生 migrationDetail，包含 migrationIn 或 migrationOut
      const record = detail?.migrationIn || detail?.migrationOut
      const direction = businessType === 'MIGRATION_IN' ? 'in' : 'out'
      return hasCompleteMigrationMaterials(direction, record?.migrationType, materials)
    }
  })
  
  const floatingHandler = createFloatingHandler({
    getFloatingApplicationDetail,
    executeFloatingApplication,
    getFloatingMaterialOptions,
    hasVerifiedFloatingMaterials
  })
  
  const residencePermitHandler = createResidencePermitHandler({
    getPermitApplicationDetail,
    issueResidencePermit,
    endorseResidencePermit,
    cancelResidencePermitApplication,
    getPermitMaterialOptions,
    hasVerifiedPermitMaterials
  })

  const handlers = [migrationHandler, floatingHandler, residencePermitHandler]
  
  for (const handler of handlers) {
    if (handler.supports(businessType)) {
      return handler
    }
  }
  return null
}

