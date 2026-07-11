export function createFloatingHandler(services) {
  return {
    family: 'floating',

    supports(businessType) {
      return businessType === 'FLOATING_REGISTRATION'
    },

    async loadDetail(applicationId) {
      if (!services.getFloatingApplicationDetail) return null
      return await services.getFloatingApplicationDetail(applicationId)
    },

    getDisplayDetail(detail) {
      return detail?.professional || null
    },

    getSubject(detail) {
      return detail?.subject || null
    },

    buildEditRoute({ applicationId, detail }) {
      return {
        path: '/floating-population/apply',
        query: { applicationId }
      }
    },

    getEditPermission(businessType) {
      return 'floating:edit'
    },

    getMaterialOptions({ businessType, detail }) {
      if (!services.getFloatingMaterialOptions) return []
      return services.getFloatingMaterialOptions()
    },

    getMaterialRuleText({ businessType, detail }) {
      return null
    },

    hasVerifiedMaterials({ businessType, detail, materials }) {
      if (!services.hasVerifiedFloatingMaterials) return true
      return services.hasVerifiedFloatingMaterials({ detail, materials })
    },

    getExecutionMeta({ businessType, detail }) {
      return {
        mode: 'dialog',
        permission: 'floating:execute',
        type: '流动登记执行',
        version: detail?.professional?.version
      }
    },

    async execute({ businessType, applicationId, detail, payload }) {
      if (!services.executeFloatingApplication) return null
      const version = detail?.professional?.version
      return await services.executeFloatingApplication(applicationId, version, payload)
    },

    isCompleted({ application, detail }) {
      return application?.status === 'COMPLETED' && detail?.professional?.businessStatus === 'COMPLETED'
    }
  }
}
