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

    normalizeDetail(raw) {
      if (services.normalizeFloatingProfessional) {
        return services.normalizeFloatingProfessional(raw)
      }
      return raw || null
    },

    getDisplayDetail(detail) {
      return detail?.professional || null
    },

    getSubject(detail) {
      return detail?.subject || null
    },

    getDetailFields() {
      return []
    },

    buildEditRoute({ applicationId }) {
      return {
        path: '/floating-population/apply',
        query: { applicationId }
      }
    },

    getEditPermission() {
      return 'floating:edit'
    },

    getSubmitPermissions() {
      return ['application:submit']
    },

    getMaterialOptions({ detail }) {
      if (!services.getFloatingMaterialOptions) return []
      return services.getFloatingMaterialOptions(detail?.professional?.residenceReasonCode)
    },

    getMaterialRuleText({ detail }) {
      if (!services.getFloatingMaterialRuleText) return ''
      return services.getFloatingMaterialRuleText(detail?.professional?.residenceReasonCode)
    },

    hasVerifiedMaterials({ detail, materials }) {
      if (!services.hasVerifiedFloatingMaterials) return false
      return services.hasVerifiedFloatingMaterials(materials, detail?.professional?.residenceReasonCode)
    },

    getExecutionMeta({ detail }) {
      return {
        mode: 'dialog',
        permission: 'floating:execute',
        type: '流动登记',
        title: '执行流动登记',
        message: '执行后将生成正式流动登记记录。',
        dialogType: 'FLOATING_EXECUTE',
        version: detail?.professional?.version
      }
    },

    async execute({ applicationId, detail, payload }) {
      if (!services.executeFloatingApplication) return null
      const version = payload?.version ?? detail?.professional?.version
      return await services.executeFloatingApplication(applicationId, version)
    },

    isCompleted({ application, detail }) {
      return application?.status === 'COMPLETED' && detail?.professional?.businessStatus === 'COMPLETED'
    }
  }
}
