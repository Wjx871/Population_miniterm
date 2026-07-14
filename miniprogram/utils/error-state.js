const ERROR_STATES = Object.freeze({
  network: { title: '网络连接失败', description: '请检查网络连接后重试' },
  unauthorized: { title: '登录已失效', description: '请重新登录后继续操作' },
  forbidden: { title: '无权访问', description: '当前账号无权查看此内容' },
  notFound: { title: '记录不存在', description: '记录可能已删除或当前账号不可见' },
  conflict: { title: '数据状态已发生变化', description: '请刷新页面后重新确认' },
  server: { title: '系统服务异常', description: '服务暂时不可用，请稍后重试' },
  unknown: { title: '加载失败', description: '请稍后重试' }
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
  if (/系统服务|服务异常|服务器/.test(value)) return 'server'
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
