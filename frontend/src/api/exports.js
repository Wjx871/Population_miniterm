import request from './request.js'
import { toSpringPageParams } from '../utils/page.js'

export function createNormalExport(payload) {
  return request({ url: '/exports/normal', method: 'post', data: payload })
}

export function createSensitiveExportApplication(payload) {
  return request({ url: '/exports/sensitive/applications', method: 'post', data: payload })
}

export const createSensitiveExport = createSensitiveExportApplication

export function getExportApplicationDetail(applicationId) {
  return request({ url: `/exports/applications/${applicationId}`, method: 'get' })
}

export const getExportApplication = getExportApplicationDetail

export function executeSensitiveExport(applicationId, version) {
  return request({url:`/exports/sensitive/applications/${applicationId}/execute`,method:'post',data:{version}})
}

export function getExportPage(params) {
  return request({ url: '/exports', method: 'get', params: toSpringPageParams(params) })
}

export function getExportDetail(exportId) {
  return request({ url: `/exports/${exportId}`, method: 'get' })
}

/** 返回完整 Axios Response，供 downloadBlob 解析 headers */
export function downloadExportFile(exportId) {
  return request({
    url: `/exports/${exportId}/download`,
    method: 'get',
    responseType: 'blob',
    rawResponse: true
  })
}

/** 页面直接消费 Blob；保留完整响应版本供文件名解析服务使用。 */
export function downloadExport(exportId) {
  return request({ url: `/exports/${exportId}/download`, method: 'get', responseType: 'blob' })
}
