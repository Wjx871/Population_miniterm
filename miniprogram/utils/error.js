const DEFAULT_MESSAGES = {
  400: '提交的信息有误，请检查后重试', 401: '登录状态已失效，请重新登录',
  403: '当前账号没有此功能的使用权限', 404: '未找到对应记录，记录可能已被更新',
  409: '当前记录状态已变化，请刷新后重试', 500: '暂时无法完成操作，请稍后重试'
}
function apiError(statusCode, message, raw) {
  const error = new Error(message || DEFAULT_MESSAGES[statusCode] || '网络请求失败，请稍后重试')
  error.statusCode = statusCode || 0
  error.raw = raw
  return error
}
function messageOf(error) { return (error && error.message) || '网络请求失败，请稍后重试' }
module.exports = { DEFAULT_MESSAGES, apiError, messageOf }
