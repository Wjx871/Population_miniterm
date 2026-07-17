const ERROR_STATES = Object.freeze({
  network: { title: '网络连接异常', description: '请检查网络后重新加载' },
  unauthorized: { title: '登录已失效', description: '请重新登录后继续操作' },
  forbidden: { title: '无权使用此功能', description: '当前账号没有此功能的使用权限' },
  notFound: { title: '未找到对应记录', description: '记录可能已被更新，请返回列表确认' },
  conflict: { title: '记录状态已更新', description: '请刷新后重试' },
  server: { title: '暂时无法完成操作', description: '请稍后重新加载' },
  unknown: { title: '加载失败', description: '请重新加载；仍未恢复时请稍后再试' }
})

const CODE_TYPES = { 0: 'network', 401: 'unauthorized', 403: 'forbidden', 404: 'notFound', 409: 'conflict', 500: 'server' }

function inferType(type, statusCode, message) {
  if (ERROR_STATES[type]) return type
  if (CODE_TYPES[Number(statusCode)] !== undefined) return CODE_TYPES[Number(statusCode)]
  const value = typeof message === 'string' ? message : ''
  if (/网络|不可达|连接失败/.test(value)) return 'network'
  if (/登录.*失效|未登录|认证/.test(value)) return 'unauthorized'
  if (/无权|权限不足|禁止访问/.test(value)) return 'forbidden'
  if (/不存在|不可见/.test(value)) return 'notFound'
  if (/冲突|状态.*变化|已被处理/.test(value)) return 'conflict'
  if (/系统服务|服务异常|服务器|暂时无法完成/.test(value)) return 'server'
  return 'unknown'
}

function publicMessage(message) {
  if (typeof message !== 'string') return ''
  const value = message.trim()
  if (!value || /exception|stack\s*trace|\bjava\.|\borg\.|\bsql\b|select\s+.+\s+from/i.test(value)) return ''
  return value
}

function resolveErrorState({ type = '', statusCode = -1, message = '' } = {}) {
  const resolvedType = inferType(type, statusCode, message)
  const preset = ERROR_STATES[resolvedType]
  const safeMessage = publicMessage(message)
  return {
    type: resolvedType,
    title: preset.title,
    description: safeMessage && safeMessage !== preset.title ? safeMessage : preset.description
  }
}

module.exports = { ERROR_STATES, resolveErrorState }
