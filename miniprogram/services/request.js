const { BASE_URL } = require('../config/index')
const storage = require('../utils/storage')
const { DEFAULT_MESSAGES, apiError } = require('../utils/error')

let redirecting401 = false

function headers(extra) {
  const token = storage.getToken()
  return Object.assign({ 'content-type': 'application/json' }, token ? { Authorization: `Bearer ${token}` } : {}, extra || {})
}

function expireSession() {
  storage.clearSession()
  try { getApp().setUser(null) } catch (error) {}
  if (redirecting401) return
  redirecting401 = true
  wx.reLaunch({ url: '/pages/login/index' })
}

function parseResponse(response) {
  const statusCode = response.statusCode
  const body = response.data || {}
  if (statusCode >= 200 && statusCode < 300 && body.code >= 200 && body.code < 300) return body.data
  const code = statusCode || body.code || 0
  if (code === 401) expireSession()
  if (code === 403) wx.showToast({ title: '权限不足', icon: 'none' })
  throw apiError(code, (code === 400 || code === 409) && body.message ? body.message : DEFAULT_MESSAGES[code], body)
}

function request(options) {
  return new Promise((resolve, reject) => {
    wx.request({
      url: `${BASE_URL}${options.url}`,
      method: options.method || 'GET',
      data: options.data,
      header: headers(options.header),
      timeout: options.timeout || 15000,
      success(response) { try { resolve(parseResponse(response)) } catch (error) { reject(error) } },
      fail(error) { reject(apiError(0, '后端服务不可达，请检查网络和服务地址', error)) }
    })
  })
}

function uploadFile(options) {
  return new Promise((resolve, reject) => wx.uploadFile({
    url: `${BASE_URL}${options.url}`, filePath: options.filePath, name: options.name || 'file',
    formData: options.formData || {}, header: headers({ 'content-type': 'multipart/form-data' }),
    success(response) {
      let data
      try { data = JSON.parse(response.data) } catch (error) { reject(apiError(response.statusCode, '文件响应解析失败', response)); return }
      try { resolve(parseResponse({ statusCode: response.statusCode, data })) } catch (error) { reject(error) }
    },
    fail(error) { reject(apiError(0, '文件上传失败，请检查网络', error)) }
  }))
}

function downloadFile(options) {
  return new Promise((resolve, reject) => wx.downloadFile({
    url: `${BASE_URL}${options.url}`, header: headers(), timeout: 30000,
    success(response) {
      if (response.statusCode === 200) resolve(response.tempFilePath)
      else { if (response.statusCode === 401) expireSession(); reject(apiError(response.statusCode, DEFAULT_MESSAGES[response.statusCode])) }
    },
    fail(error) { reject(apiError(0, '文件下载失败，请检查网络', error)) }
  }))
}

function reset401Guard() { redirecting401 = false }
module.exports = { request, uploadFile, downloadFile, parseResponse, headers, expireSession, reset401Guard }
