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
      return services.getFloatingMaterialOptions(detail?.professional?.residenceReasonCode)
    },

    getMaterialRuleText({ businessType, detail }) {
      if (!services.getFloatingMaterialRuleText) return ''
      return services.getFloatingMaterialRuleText(detail?.professional?.residenceReasonCode)
    },

    hasVerifiedMaterials({ businessType, detail, materials }) {
      if (!services.hasVerifiedFloatingMaterials) return false
      return services.hasVerifiedFloatingMaterials(materials, detail?.professional?.residenceReasonCode)
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
      const version = payload?.version ?? detail?.professional?.version
      return await services.executeFloatingApplication(applicationId, version)
    },

    isCompleted({ application, detail }) {
      return application?.status === 'COMPLETED' && detail?.professional?.businessStatus === 'COMPLETED'
    }
  }
}
