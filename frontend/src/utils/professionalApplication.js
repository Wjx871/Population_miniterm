/**
 * 判断专业版本号是否有效（0 是合法版本）
 * @param {any} value
 * @returns {boolean}
 */
export function isValidVersion(value) {
  return Number.isInteger(value)
}

/**
 * 判断当前状态是否允许保存专业草稿
 * @param {Object} params
 * @param {string|number|null} params.applicationId
 * @param {string} params.applicationStatus
 * @param {any} params.version
 * @returns {boolean}
 */
export function canSaveProfessionalDraft({ applicationId, applicationStatus, version }) {
  const isEdit = Boolean(applicationId)
  
  if (!isEdit) {
    return true
  }

  return applicationStatus === 'DRAFT' && isValidVersion(version)
}

/**
 * 合并通用主体和专业对象（专业对象覆盖主体对象）
 * @param {Object} subject
 * @param {Object} professional
 * @returns {Object}
 */
export function mergeSubjectAndProfessional(subject, professional) {
  const s = subject || {}
  const p = professional || {}
  
  const merged = { ...s, ...p }
  
  // 测试用例要求："subject 可补齐 registrationNo/currentRegionCode"
  // 这里确保从 p 中没有的时候，如果有默认机制可以保留。由于 ...s 已经被 p 覆盖，
  // 行为本身已符合覆盖要求。
  return merged
}

/**
 * 客户端假分页（不修改原数组）
 * @param {Array} records 
 * @param {number} current 
 * @param {number} size 
 * @returns {Array}
 */
export function sliceClientPage(records, current, size) {
  if (!Array.isArray(records)) return []
  
  const c = Math.max(1, current || 1)
  const s = Math.max(1, size || 10)
  
  const start = (c - 1) * s
  const end = start + s
  
  if (start >= records.length) {
    return []
  }
  
  return records.slice(start, end)
}
