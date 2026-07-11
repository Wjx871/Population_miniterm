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

    getDisplayDetail(detail) {
      return detail?.migration || null
    },

    getSubject(detail) {
      return detail?.subject || null
    },

    buildEditRoute({ applicationId, detail }) {
      // 迁入/迁出对应的编辑路由是各自独立的，但原路由应该是根据业务类型来定
      // 假设原路由逻辑是 MIGRATION_IN -> /migration/in/apply, MIGRATION_OUT -> /migration/out/apply 
      // 或者是统一的，这里交给实际业务去决定或根据之前原有的逻辑
      // 我们在此简单返回，但需要原先页面怎么写的，我们可以交给 services 返回
      const businessType = detail?.application?.businessType
      if (businessType === 'MIGRATION_IN') {
        return { path: '/migration/in/apply', query: { applicationId } }
      }
      return { path: '/migration/out/apply', query: { applicationId } }
    },

    getEditPermission(businessType) {
      if (businessType === 'MIGRATION_IN') return 'migration:in:create'
      if (businessType === 'MIGRATION_OUT') return 'migration:out:create'
      return null
    },

    getMaterialOptions({ businessType, detail }) {
      if (!services.getMigrationMaterialOptions) return []
      return services.getMigrationMaterialOptions({ businessType, detail })
    },

    getMaterialRuleText({ businessType, detail }) {
      // 在审批页原来不需要特别复杂的 rule text（或者是通用的）
      // 返回 null 让外面使用通用提示
      return null 
    },

    hasVerifiedMaterials({ businessType, detail, materials }) {
      if (!services.hasCompleteMigrationMaterials) return true // 回退，假定没有也过
      return services.hasCompleteMigrationMaterials({ businessType, detail, materials })
    },

    getExecutionMeta({ businessType, detail }) {
      return {
        mode: 'direct-confirm',
        permission: 'migration:execute',
        type: '迁移执行',
        version: detail?.migration?.version
      }
    },

    async execute({ businessType, applicationId, detail, payload }) {
      const version = detail?.migration?.version
      if (businessType === 'MIGRATION_IN') {
        return await services.executeMigrationIn(applicationId, version)
      } else if (businessType === 'MIGRATION_OUT') {
        return await services.executeMigrationOut(applicationId, version)
      }
      return null
    },

    isCompleted({ application, detail }) {
      return application?.status === 'COMPLETED' && detail?.migration?.businessStatus === 'COMPLETED'
    }
  }
}
