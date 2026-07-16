function validateCredentials(username, password) {
  if (!String(username || '').trim()) return '请输入用户名'
  if (!password) return '请输入密码'
  return ''
}

function safeLoginError(error) {
  const code = Number(error && error.statusCode)
  if (code === 0) return '网络连接失败，请检查网络后重试'
  if (code === 401) return '用户名或密码错误'
  if (code === 403) return '当前账号无权登录'
  if (code >= 500) return '暂时无法登录，请稍后重试'
  const message = error && typeof error.message === 'string' ? error.message.trim() : ''
  if (!message || /exception|stack\s*trace|\bjava\.|\borg\.|\bsql\b/i.test(message)) return '登录失败，请稍后重试'
  return message
}

module.exports = { validateCredentials, safeLoginError }
