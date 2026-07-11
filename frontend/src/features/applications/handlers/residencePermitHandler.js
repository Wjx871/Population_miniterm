const APPLY_TYPE_MAP = {
  RESIDENCE_PERMIT_FIRST_ISSUE: 'FIRST_ISSUE',
  RESIDENCE_PERMIT_ENDORSEMENT: 'ENDORSEMENT',
  RESIDENCE_PERMIT_CANCELLATION: 'CANCELLATION'
}

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
      
      if (businessType === 'RESIDENCE_PERMIT_FIRST_ISSUE') {
        return {
          path: '/residence-permits/first-issue',
          query: { applicationId }
        }
      }

      const permitId = detail?.professional?.permitId
      if (businessType === 'RESIDENCE_PERMIT_ENDORSEMENT') {
        return {
          path: `/residence-permits/${permitId}/endorsement/apply`,
          query: { applicationId, permitId, applyType: 'ENDORSEMENT' }
        }
      }
      
      if (businessType === 'RESIDENCE_PERMIT_CANCELLATION') {
        return {
          path: `/residence-permits/${permitId}/cancellation/apply`,
          query: { applicationId, permitId, applyType: 'CANCELLATION' }
        }
      }
      return null
    },

    getEditPermission(businessType) {
      return 'residence-permit:apply'
    },

    getMaterialOptions({ businessType, detail }) {
      if (!services.getPermitMaterialOptions) return []
      const applyType = APPLY_TYPE_MAP[businessType]
      return services.getPermitMaterialOptions(applyType, detail?.subject?.residenceReasonCode)
    },

    getMaterialRuleText({ businessType, detail }) {
      if (!services.getPermitMaterialRuleText) return ''
      const applyType = APPLY_TYPE_MAP[businessType]
      return services.getPermitMaterialRuleText(applyType, detail?.subject?.residenceReasonCode)
    },

    hasVerifiedMaterials({ businessType, detail, materials }) {
      if (!services.hasVerifiedPermitMaterials) return false
      const applyType = APPLY_TYPE_MAP[businessType]
      return services.hasVerifiedPermitMaterials(materials, applyType, detail?.subject?.residenceReasonCode)
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
      const version = payload?.version ?? detail?.professional?.version
      
      if (businessType === 'RESIDENCE_PERMIT_FIRST_ISSUE') {
        return await services.issueResidencePermit(applicationId, {
          issuingAuthority: payload?.issuingAuthority,
          version
        })
      } else if (businessType === 'RESIDENCE_PERMIT_ENDORSEMENT') {
        return await services.endorseResidencePermit(applicationId, version)
      } else if (businessType === 'RESIDENCE_PERMIT_CANCELLATION') {
        return await services.cancelResidencePermitApplication(applicationId, version)
      }
      return null
    },

    isCompleted({ application, detail }) {
      return application?.status === 'COMPLETED' && detail?.professional?.businessStatus === 'COMPLETED'
    }
  }
}
