import { ref, computed } from 'vue'
import { canSaveProfessionalDraft, isValidVersion } from '../utils/professionalApplication.js'

export function useProfessionalDraftState() {
  const applicationId = ref(null)
  const applicationStatus = ref(null)
  const professionalVersion = ref(null)

  const isEdit = computed(() => Boolean(applicationId.value))
  
  // 按照约束：非 DRAFT 状态只读
  const isReadOnly = computed(() => isEdit.value && applicationStatus.value !== 'DRAFT')
  
  // 按照约束：如果是编辑状态，必须有合法 version，否则报错保护。新建时不要求。
  const hasValidVersion = computed(() => {
    return canSaveProfessionalDraft({
      applicationId: applicationId.value,
      applicationStatus: applicationStatus.value,
      version: professionalVersion.value
    })
  })

  function applyDetailMeta(detail) {
    if (!detail) return
    
    applicationId.value = detail.application?.applicationId || null
    applicationStatus.value = detail.application?.status || null
    
    // 读取专业版本的通用方式。不同业务可能在不同字段（如 detail.professional.version 或 detail.migration.version 等）
    // 为了不导入具体业务知识，我们尽力去尝试读取，或者要求外部规整传入。
    // 但是考虑到 detail 的结构通常是 { application, professional: { version } } 或 { application, migration: { version } }
    // 我们在此简单处理，查找存在的版本
    let version = null
    if (detail.professional && 'version' in detail.professional) {
      version = detail.professional.version
    } else if (detail.migration && 'version' in detail.migration) {
      version = detail.migration.version
    }
    
    // 缺失时保存为 null，不得默认 0
    professionalVersion.value = isValidVersion(version) ? version : null
  }

  function markCreated(newApplicationId) {
    applicationId.value = newApplicationId
    applicationStatus.value = 'DRAFT'
    professionalVersion.value = null // 不猜测 version，等待页面重载
  }

  function clear() {
    applicationId.value = null
    applicationStatus.value = null
    professionalVersion.value = null
  }

  return {
    applicationId,
    applicationStatus,
    professionalVersion,
    isEdit,
    isReadOnly,
    hasValidVersion,
    applyDetailMeta,
    markCreated,
    clear
  }
}
