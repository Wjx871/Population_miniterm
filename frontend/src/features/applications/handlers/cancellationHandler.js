import {
  CANCEL_OBJECT_TYPE,
  getCancellationMaterialOptions,
  getCancellationMaterialRuleText,
  hasVerifiedCancellationMaterials
} from '../../../constants/cancellation.js'
import { getCancellationDetailFields } from '../../../adapters/cancellation.js'

export function createCancellationHandler(services) {
  return {
    family: 'cancellation',

    supports(businessType) {
      return businessType === 'PERSON_CANCELLATION' || businessType === 'HOUSEHOLD_CANCELLATION'
    },

    async loadDetail(applicationId) {
      if (!services.getCancellationApplicationDetail) return null
      return await services.getCancellationApplicationDetail(applicationId)
    },

    normalizeDetail(raw) {
      if (services.normalizeCancellationProfessional) {
        return services.normalizeCancellationProfessional(raw)
      }
      return raw || null
    },

    getDisplayDetail(detail) {
      return detail?.cancellation || null
    },

    getSubject(detail) {
      return detail?.target || null
    },

    getDetailTitle() {
      return '注销专业信息'
    },

    getDetailStatus(detail) {
      return detail?.cancellation?.businessStatus || ''
    },

    getDetailStatusKind() {
      return 'application'
    },

    getDetailFields(detail) {
      return getCancellationDetailFields(detail)
    },

    buildEditRoute({ applicationId, detail }) {
      const objectType = detail?.cancellation?.cancelObjectType
        || detail?.application?.businessType
      const isHousehold = objectType === CANCEL_OBJECT_TYPE.HOUSEHOLD
        || objectType === 'HOUSEHOLD_CANCELLATION'
      return {
        path: '/cancellations/apply',
        query: {
          applicationId,
          objectType: isHousehold ? CANCEL_OBJECT_TYPE.HOUSEHOLD : CANCEL_OBJECT_TYPE.PERSON
        }
      }
    },

    getEditPermission(businessType) {
      if (businessType === 'HOUSEHOLD_CANCELLATION') return 'cancellation:household:create'
      return 'cancellation:person:create'
    },

    getSubmitPermissions() {
      return ['application:submit']
    },

    getMaterialOptions({ businessType, detail }) {
      const objectType = businessType === 'HOUSEHOLD_CANCELLATION'
        ? CANCEL_OBJECT_TYPE.HOUSEHOLD
        : CANCEL_OBJECT_TYPE.PERSON
      const reasonCode = detail?.cancellation?.cancelReasonCode
      if (services.getCancellationMaterialOptions) {
        return services.getCancellationMaterialOptions(objectType, reasonCode)
      }
      return getCancellationMaterialOptions(objectType, reasonCode)
    },

    getMaterialRuleText({ businessType, detail }) {
      const objectType = businessType === 'HOUSEHOLD_CANCELLATION'
        ? CANCEL_OBJECT_TYPE.HOUSEHOLD
        : CANCEL_OBJECT_TYPE.PERSON
      const reasonCode = detail?.cancellation?.cancelReasonCode
      if (services.getCancellationMaterialRuleText) {
        return services.getCancellationMaterialRuleText(objectType, reasonCode)
      }
      return getCancellationMaterialRuleText(objectType, reasonCode)
    },

    hasVerifiedMaterials({ businessType, detail, materials }) {
      const objectType = businessType === 'HOUSEHOLD_CANCELLATION'
        ? CANCEL_OBJECT_TYPE.HOUSEHOLD
        : CANCEL_OBJECT_TYPE.PERSON
      const reasonCode = detail?.cancellation?.cancelReasonCode
      if (services.hasVerifiedCancellationMaterials) {
        return services.hasVerifiedCancellationMaterials(objectType, reasonCode, materials)
      }
      return hasVerifiedCancellationMaterials(objectType, reasonCode, materials)
    },

    getExecutionMeta({ businessType, detail }) {
      const isHousehold = businessType === 'HOUSEHOLD_CANCELLATION'
      return {
        mode: 'direct-confirm',
        permission: 'cancellation:execute',
        type: isHousehold ? '家庭户销户' : '人员注销',
        title: isHousehold ? '执行家庭户销户' : '执行人员注销',
        message: isHousehold
          ? '执行后将销户并生成家庭户归档快照，确认继续吗？'
          : '执行后将更新人员状态并可能生成户籍归档，确认继续吗？',
        version: detail?.cancellation?.version
      }
    },

    async execute({ businessType, applicationId, detail, payload }) {
      const version = payload?.version ?? detail?.cancellation?.version
      if (businessType === 'HOUSEHOLD_CANCELLATION') {
        return await services.executeHouseholdCancellation(applicationId, version)
      }
      return await services.executePersonCancellation(applicationId, version)
    },

    isCompleted({ application, detail }) {
      return application?.status === 'COMPLETED'
        && detail?.cancellation?.businessStatus === 'COMPLETED'
    }
  }
}
