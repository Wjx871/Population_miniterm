import request from './request'
import { toSpringPageParams } from '../utils/page'

// ==================== 流动登记专业申请 (7) ====================

export function createFloatingApplication(payload) {
  return request({ url: '/floating-registrations/applications', method: 'post', data: payload })
}

export function updateFloatingApplication(applicationId, payload) {
  return request({ url: `/floating-registrations/applications/${applicationId}`, method: 'put', data: payload })
}

export function getFloatingApplicationDetail(applicationId) {
  return request({ url: `/floating-registrations/applications/${applicationId}`, method: 'get' })
}

export function executeFloatingApplication(applicationId, version) {
  return request({ url: `/floating-registrations/applications/${applicationId}/execute`, method: 'post', data: { version } })
}

export function getFloatingPopulationPage(params) {
  return request({ url: '/floating-populations', method: 'get', params: toSpringPageParams(params) })
}

export function getFloatingPopulationById(floatingId) {
  return request({ url: `/floating-populations/${floatingId}`, method: 'get' })
}

export function closeFloatingPopulation(floatingId, payload) {
  return request({ url: `/floating-populations/${floatingId}/close`, method: 'post', data: payload })
}

// ==================== 居住证专业申请与执行 (8) ====================

export function createPermitFirstIssueApplication(payload) {
  return request({ url: '/residence-permits/applications/first-issue', method: 'post', data: payload })
}

export function createPermitEndorsementApplication(permitId, payload) {
  return request({ url: `/residence-permits/applications/${permitId}/endorsement`, method: 'post', data: payload })
}

export function createPermitCancellationApplication(permitId, payload) {
  return request({ url: `/residence-permits/applications/${permitId}/cancellation`, method: 'post', data: payload })
}

export function updatePermitApplication(applicationId, payload) {
  return request({ url: `/residence-permits/applications/${applicationId}`, method: 'put', data: payload })
}

export function getPermitApplicationDetail(applicationId) {
  return request({ url: `/residence-permits/applications/${applicationId}`, method: 'get' })
}

export function issueResidencePermit(applicationId, payload) {
  return request({ url: `/residence-permits/applications/${applicationId}/issue`, method: 'post', data: payload })
}

export function endorseResidencePermit(applicationId, version) {
  return request({ url: `/residence-permits/applications/${applicationId}/endorse`, method: 'post', data: { version } })
}

export function cancelResidencePermitApplication(applicationId, version) {
  return request({ url: `/residence-permits/applications/${applicationId}/cancel`, method: 'post', data: { version } })
}

// ==================== 正式居住证查询 (4) ====================

export function getResidencePermitPage(params) {
  return request({ url: '/residence-permits', method: 'get', params: toSpringPageParams(params) })
}

export function getResidencePermitById(permitId) {
  return request({ url: `/residence-permits/${permitId}`, method: 'get' })
}

export function getResidencePermitLogs(permitId) {
  return request({ url: `/residence-permits/${permitId}/logs`, method: 'get' })
}

export function getExpiringResidencePermits(params) {
  return request({ url: '/residence-permits/expiring', method: 'get', params })
}
