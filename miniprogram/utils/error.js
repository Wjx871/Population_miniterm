const DEFAULT_MESSAGES = {
  400: '请求参数有误', 401: '登录状态已失效', 403: '权限不足',
  404: '记录不存在或无权查看', 409: '业务状态已变化，请刷新后重试',
  500: '系统服务异常，请稍后重试'
}
function apiError(statusCode, message, raw) {
  const error = new Error(message || DEFAULT_MESSAGES[statusCode] || '网络请求失败，请稍后重试')
  error.statusCode = statusCode || 0
  error.raw = raw
  return error
}
function messageOf(error) { return (error && error.message) || '网络请求失败，请稍后重试' }
module.exports = { DEFAULT_MESSAGES, apiError, messageOf }
