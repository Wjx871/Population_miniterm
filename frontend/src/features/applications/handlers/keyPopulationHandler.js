import {
  getKeyPopulationMaterialOptions,
  getKeyPopulationMaterialRuleText,
  hasVerifiedKeyPopulationMaterials
} from '../../../constants/keyPopulation.js'
import { getKeyPopulationDetailFields } from '../../../adapters/keyPopulation.js'

export function createKeyPopulationHandler(services) {
  return {
    family: 'keyPopulation',

    supports(businessType) {
      return businessType === 'KEY_POPULATION_REGISTER' || businessType === 'KEY_POPULATION_RELEASE'
    },

    async loadDetail(applicationId, businessType) {
      if (businessType === 'KEY_POPULATION_RELEASE' && services.getReleaseApplicationDetail) {
        return await services.getReleaseApplicationDetail(applicationId)
      }
      if (businessType === 'KEY_POPULATION_REGISTER' && services.getRegisterApplicationDetail) {
        return await services.getRegisterApplicationDetail(applicationId)
      }
      // 未知类型时按建档→解除回退（兼容旧调用）
      if (services.getRegisterApplicationDetail) {
        try {
          return await services.getRegisterApplicationDetail(applicationId)
        } catch {
          /* try release */
        }
      }
      if (services.getReleaseApplicationDetail) {
        return await services.getReleaseApplicationDetail(applicationId)
      }
      return null
    },

    normalizeDetail(raw) {
      if (services.normalizeKeyPopulationApplication) {
        return services.normalizeKeyPopulationApplication(raw)
      }
      return raw || null
    },

    getDisplayDetail(detail) {
      return detail?.professional || null
    },

    getSubject() {
      return null
    },

    getDetailTitle(detail) {
      return detail?.professional?.operationType === 'RELEASE'
        ? '重点人口解除申请'
        : '重点人口建档申请'
    },

    getDetailStatus(detail) {
      return detail?.professional?.businessStatus || ''
    },

    getDetailStatusKind() {
      return 'application'
    },

    getDetailFields(detail) {
      return getKeyPopulationDetailFields(detail)
    },

    /** 后端无 PUT 更新草稿 */
    buildEditRoute() {
      return null
    },

    getEditPermission() {
      return 'key-population:apply'
    },

    getMaterialOptions() {
      if (services.getKeyPopulationMaterialOptions) {
        return services.getKeyPopulationMaterialOptions()
      }
      return getKeyPopulationMaterialOptions()
    },

    getMaterialRuleText() {
      if (services.getKeyPopulationMaterialRuleText) {
        return services.getKeyPopulationMaterialRuleText()
      }
      return getKeyPopulationMaterialRuleText()
    },

    hasVerifiedMaterials({ materials }) {
      if (services.hasVerifiedKeyPopulationMaterials) {
        return services.hasVerifiedKeyPopulationMaterials(materials)
      }
      return hasVerifiedKeyPopulationMaterials(materials)
    },

    async submit({ businessType, applicationId }) {
      if (businessType === 'KEY_POPULATION_REGISTER') {
        return await services.submitRegisterApplication(applicationId)
      }
      if (businessType === 'KEY_POPULATION_RELEASE') {
        return await services.submitReleaseApplication(applicationId)
      }
      return null
    },

    getExecutionMeta({ businessType, detail }) {
      const isRelease = businessType === 'KEY_POPULATION_RELEASE'
      return {
        mode: 'direct-confirm',
        permission: 'key-population:execute',
        type: isRelease ? '解除重点人口' : '建档重点人口',
        title: isRelease ? '执行重点人口解除' : '执行重点人口建档',
        message: isRelease
          ? '执行后当前记录将变为已解除并追加历史，确认继续吗？'
          : '执行后将写入重点人口当前记录并追加历史，确认继续吗？',
        version: detail?.professional?.version
      }
    },

    async execute({ businessType, applicationId, detail, payload }) {
      const version = payload?.version ?? detail?.professional?.version
      if (businessType === 'KEY_POPULATION_REGISTER') {
        return await services.executeRegisterApplication(applicationId, version)
      }
      if (businessType === 'KEY_POPULATION_RELEASE') {
        return await services.executeReleaseApplication(applicationId, version)
      }
      return null
    },

    isCompleted({ application, detail }) {
      return application?.status === 'COMPLETED'
        && (detail?.professional?.businessStatus === 'COMPLETED' || application?.status === 'COMPLETED')
    }
  }
}
