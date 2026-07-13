import request from './request'
import { toSpringPageParams } from '../utils/page'

export function createNormalExport(payload) {
  return request({ url: '/exports/normal', method: 'post', data: payload })
}

export function createSensitiveExportApplication(payload) {
  return request({ url: '/exports/sensitive/applications', method: 'post', data: payload })
}

export function getExportApplicationDetail(applicationId) {
  return request({ url: `/exports/applications/${applicationId}`, method: 'get' })
}

export function executeSensitiveExport(applicationId, version) {
  return request({
    url: `/exports/sensitive/applications/${applicationId}/execute`,
    method: 'post',
    data: { version }
  })
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
