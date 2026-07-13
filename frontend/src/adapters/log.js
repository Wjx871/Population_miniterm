const pickFirst = (...values) => {
  for (const v of values) {
    if (v !== undefined && v !== null) return v
  }
  return undefined
}

export function normalizeLogRecord(raw) {
  if (!raw) return null
  return {
    logId: pickFirst(raw.logId, raw.id),
    userId: raw.userId,
    username: raw.username || '',
    operationType: raw.operationType || '',
    moduleName: raw.moduleName || raw.module || '',
    requestPath: raw.requestPath || '',
    requestMethod: raw.requestMethod || '',
    operationTime: raw.operationTime || '',
    ipAddress: raw.ipAddress || raw.ip || '',
    operationResult: raw.operationResult || raw.result || '',
    errorMessage: raw.errorMessage || '',
    detail: raw.detail || ''
  }
}

export function normalizeLogList(records) {
  if (!Array.isArray(records)) return []
  return records.filter((r) => r != null).map(normalizeLogRecord)
}

/** 操作日志筛选：仅冻结真实参数 */
export function toOperationLogQueryParams(query) {
  const params = { current: query.current, size: query.size }
  for (const key of ['username', 'operationType', 'module', 'result', 'ip', 'dateFrom', 'dateTo']) {
    if (query[key] !== undefined && query[key] !== null && query[key] !== '') {
      params[key] = query[key]
    }
  }
  return params
}

/** 登录日志筛选：仅冻结真实参数 */
export function toLoginLogQueryParams(query) {
  const params = { current: query.current, size: query.size }
  for (const key of ['username', 'result', 'ip', 'dateFrom', 'dateTo']) {
    if (query[key] !== undefined && query[key] !== null && query[key] !== '') {
      params[key] = query[key]
    }
  }
  return params
}
