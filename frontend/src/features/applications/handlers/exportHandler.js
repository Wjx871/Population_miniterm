import {
  getSensitiveExportMaterialOptions,
  getSensitiveExportMaterialRuleText,
  hasVerifiedSensitiveExportMaterials
} from '../../../constants/export.js'
import { getExportDetailFields } from '../../../adapters/export.js'

export function createExportHandler(services) {
  return {
    family: 'export',

    supports(businessType) {
      return businessType === 'SENSITIVE_DATA_EXPORT'
    },

    async loadDetail(applicationId) {
      if (!services.getExportApplicationDetail) return null
      return await services.getExportApplicationDetail(applicationId)
    },

    normalizeDetail(raw) {
      if (services.normalizeExportApplication) {
        return services.normalizeExportApplication(raw)
      }
      return raw || null
    },

    getDisplayDetail(detail) {
      return detail?.professional || null
    },

    getSubject() {
      return null
    },

    getDetailTitle() {
      return '敏感导出专业信息'
    },

    getDetailStatus(detail) {
      return detail?.professional?.businessStatus || detail?.application?.status || ''
    },

    getDetailStatusKind() {
      return 'application'
    },

    getDetailFields(detail) {
      return getExportDetailFields(detail)
    },

    /** 敏感导出无 PUT 更新草稿接口：创建后专业字段只读，不提供编辑路由 */
    buildEditRoute() {
      return null
    },

    getEditPermission() {
      return 'data:export:sensitive:apply'
    },

    getMaterialOptions() {
      if (services.getSensitiveExportMaterialOptions) {
        return services.getSensitiveExportMaterialOptions()
      }
      return getSensitiveExportMaterialOptions()
    },

    getMaterialRuleText() {
      if (services.getSensitiveExportMaterialRuleText) {
        return services.getSensitiveExportMaterialRuleText()
      }
      return getSensitiveExportMaterialRuleText()
    },

    hasVerifiedMaterials({ materials }) {
      if (services.hasVerifiedSensitiveExportMaterials) {
        return services.hasVerifiedSensitiveExportMaterials(materials)
      }
      return hasVerifiedSensitiveExportMaterials(materials)
    },

    getExecutionMeta({ detail }) {
      return {
        mode: 'direct-confirm',
        permission: 'data:export:sensitive:execute',
        type: '敏感导出',
        title: '执行敏感导出',
        message: '执行后将按批准范围生成导出文件，确认继续吗？',
        version: detail?.professional?.version
      }
    },

    async execute({ applicationId, detail, payload }) {
      const version = payload?.version ?? detail?.professional?.version
      return await services.executeSensitiveExport(applicationId, version)
    },

    isCompleted({ application }) {
      return application?.status === 'COMPLETED'
    }
  }
}
