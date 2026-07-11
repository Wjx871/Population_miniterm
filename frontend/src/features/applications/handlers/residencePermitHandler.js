export function createResidencePermitHandler(services) {
  return {
    family: 'permit',

    supports(businessType) {
      return [
        'RESIDENCE_PERMIT_FIRST_ISSUE',
        'RESIDENCE_PERMIT_ENDORSEMENT',
        'RESIDENCE_PERMIT_CANCELLATION'
      ].includes(businessType)
    },

    async loadDetail(applicationId) {
      if (!services.getPermitApplicationDetail) return null
      return await services.getPermitApplicationDetail(applicationId)
    },

    getDisplayDetail(detail) {
      return detail?.professional || null
    },

    getSubject(detail) {
      return detail?.subject || null
    },

    buildEditRoute({ applicationId, detail }) {
      const businessType = detail?.application?.businessType
      const query = { applicationId }
      
      // 签注和注销保留 permitId 和 applyType
      if (businessType === 'RESIDENCE_PERMIT_ENDORSEMENT' || businessType === 'RESIDENCE_PERMIT_CANCELLATION') {
        if (detail?.professional?.permitId) {
          query.permitId = detail.professional.permitId
        }
        query.applyType = businessType === 'RESIDENCE_PERMIT_ENDORSEMENT' ? 'ENDORSEMENT' : 'CANCELLATION'
      }

      return {
        path: '/residence-permit/apply',
        query
      }
    },

    getEditPermission(businessType) {
      return 'residence-permit:apply'
    },

    getMaterialOptions({ businessType, detail }) {
      if (!services.getPermitMaterialOptions) return []
      
      if (businessType === 'RESIDENCE_PERMIT_FIRST_ISSUE') {
        const reasonCode = detail?.subject?.residenceReasonCode
        return services.getPermitMaterialOptions(businessType, reasonCode)
      }
      
      return services.getPermitMaterialOptions(businessType)
    },

    getMaterialRuleText({ businessType, detail }) {
      return null
    },

    hasVerifiedMaterials({ businessType, detail, materials }) {
      if (!services.hasVerifiedPermitMaterials) return true
      
      let reasonCode = null
      if (businessType === 'RESIDENCE_PERMIT_FIRST_ISSUE') {
        reasonCode = detail?.subject?.residenceReasonCode
      }
      
      return services.hasVerifiedPermitMaterials({ businessType, reasonCode, materials })
    },

    getExecutionMeta({ businessType, detail }) {
      let permission = ''
      let typeName = ''
      
      if (businessType === 'RESIDENCE_PERMIT_FIRST_ISSUE') {
        permission = 'residence-permit:issue'
        typeName = '首次签发'
      } else if (businessType === 'RESIDENCE_PERMIT_ENDORSEMENT') {
        permission = 'residence-permit:endorse'
        typeName = '签注'
      } else if (businessType === 'RESIDENCE_PERMIT_CANCELLATION') {
        permission = 'residence-permit:cancel'
        typeName = '注销'
      }

      return {
        mode: 'dialog',
        permission,
        type: typeName,
        version: detail?.professional?.version
      }
    },

    async execute({ businessType, applicationId, detail, payload }) {
      const version = detail?.professional?.version
      
      if (businessType === 'RESIDENCE_PERMIT_FIRST_ISSUE') {
        return await services.issueResidencePermit(applicationId, version, payload)
      } else if (businessType === 'RESIDENCE_PERMIT_ENDORSEMENT') {
        return await services.endorseResidencePermit(applicationId, version, payload)
      } else if (businessType === 'RESIDENCE_PERMIT_CANCELLATION') {
        return await services.cancelResidencePermitApplication(applicationId, version, payload)
      }
      return null
    },

    isCompleted({ application, detail }) {
      return application?.status === 'COMPLETED' && detail?.professional?.businessStatus === 'COMPLETED'
    }
  }
}
