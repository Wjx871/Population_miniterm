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

    normalizeDetail(raw) {
      if (services.normalizePermitProfessional) {
        return services.normalizePermitProfessional(raw)
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

    getEditPermission() {
      return 'residence-permit:apply'
    },

    getSubmitPermissions() {
      return ['application:submit']
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
      let title = ''
      let dialogType = ''

      if (businessType === 'RESIDENCE_PERMIT_FIRST_ISSUE') {
        permission = 'residence-permit:issue'
        typeName = '首次签发'
        title = '签发居住证'
        dialogType = 'PERMIT_ISSUE'
      } else if (businessType === 'RESIDENCE_PERMIT_ENDORSEMENT') {
        permission = 'residence-permit:endorse'
        typeName = '签注'
        title = '执行签注'
        dialogType = 'PERMIT_ENDORSE'
      } else if (businessType === 'RESIDENCE_PERMIT_CANCELLATION') {
        permission = 'residence-permit:cancel'
        typeName = '注销'
        title = '执行注销'
        dialogType = 'PERMIT_CANCEL'
      }

      return {
        mode: 'dialog',
        permission,
        type: typeName,
        title,
        message: '执行后将生成/变更正式居住证状态。',
        dialogType,
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
      }
      if (businessType === 'RESIDENCE_PERMIT_ENDORSEMENT') {
        return await services.endorseResidencePermit(applicationId, version)
      }
      if (businessType === 'RESIDENCE_PERMIT_CANCELLATION') {
        return await services.cancelResidencePermitApplication(applicationId, version)
      }
      return null
    },

    isCompleted({ application, detail }) {
      return application?.status === 'COMPLETED' && detail?.professional?.businessStatus === 'COMPLETED'
    }
  }
}
