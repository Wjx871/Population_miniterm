function getRecord(detail) {
  return detail?.migrationIn || detail?.migrationOut || null
}

export function createMigrationHandler(services) {
  return {
    family: 'migration',

    supports(businessType) {
      return businessType === 'MIGRATION_IN' || businessType === 'MIGRATION_OUT'
    },

    async loadDetail(applicationId) {
      if (!services.getMigrationApplicationDetail) {
        return null
      }
      return await services.getMigrationApplicationDetail(applicationId)
    },

    normalizeDetail(raw) {
      return raw || null
    },

    getDisplayDetail(detail) {
      return getRecord(detail)
    },

    getSubject(detail) {
      return detail?.subject || null
    },

    getDetailFields() {
      return []
    },

    buildEditRoute({ applicationId, detail }) {
      const businessType = detail?.application?.businessType
      if (businessType === 'MIGRATION_IN') {
        return { path: '/migrations/in/apply', query: { applicationId } }
      }
      return { path: '/migrations/out/apply', query: { applicationId } }
    },

    getEditPermission(businessType) {
      if (businessType === 'MIGRATION_IN') return 'migration:in:create'
      if (businessType === 'MIGRATION_OUT') return 'migration:out:create'
      return null
    },

    getSubmitPermissions() {
      return ['application:submit']
    },

    getMaterialOptions({ businessType, detail }) {
      if (!services.getMigrationMaterialOptions) return []
      const record = getRecord(detail)
      const direction = businessType === 'MIGRATION_IN' ? 'in' : 'out'
      return services.getMigrationMaterialOptions(direction, record?.migrationType)
    },

    getMaterialRuleText({ businessType, detail }) {
      if (!services.getMigrationMaterialRuleText) return ''
      const record = getRecord(detail)
      const direction = businessType === 'MIGRATION_IN' ? 'in' : 'out'
      return services.getMigrationMaterialRuleText(direction, record?.migrationType)
    },

    hasVerifiedMaterials({ businessType, detail, materials }) {
      if (!services.hasCompleteMigrationMaterials) return false
      const record = getRecord(detail)
      const direction = businessType === 'MIGRATION_IN' ? 'in' : 'out'
      return services.hasCompleteMigrationMaterials(direction, record?.migrationType, materials)
    },

    getExecutionMeta({ businessType, detail }) {
      const isIn = businessType === 'MIGRATION_IN'
      return {
        mode: 'direct-confirm',
        permission: 'migration:execute',
        type: isIn ? '迁入' : '迁出',
        title: isIn ? '执行迁入' : '执行迁出',
        message: '执行会由后端变更当前户籍并生成归档，确认继续吗？',
        version: getRecord(detail)?.version
      }
    },

    async execute({ businessType, applicationId, detail }) {
      const version = getRecord(detail)?.version
      if (businessType === 'MIGRATION_IN') {
        return await services.executeMigrationIn(applicationId, version)
      }
      if (businessType === 'MIGRATION_OUT') {
        return await services.executeMigrationOut(applicationId, version)
      }
      return null
    },

    isCompleted({ application, detail }) {
      return application?.status === 'COMPLETED' && getRecord(detail)?.businessStatus === 'COMPLETED'
    }
  }
}
