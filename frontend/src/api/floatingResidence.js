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
  const raw = params || {}
  const { current, size } = raw
  // 仅在用户打开「包含历史」开关时才请求历史行；否则显式传 false，方便排错并避免依赖后端默认值。
  const next = {
    registrationNo: raw.registrationNo,
    personName: raw.personName,
    identityNo: raw.identityNo,
    currentRegionCode: raw.currentRegionCode,
    status: raw.status,
    includeHistory: raw.includeHistory === true
  }
  // 仅保留有意义的查询条件（去除空串/null/undefined），避免向 URL 中追加无意义参数
  Object.keys(next).forEach((key) => {
    const value = next[key]
    if (value === '' || value === null || value === undefined) delete next[key]
  })
  return request({ url: '/floating-populations', method: 'get', params: toSpringPageParams({ ...next, current, size }) })
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
  const raw = params || {}
  const { current, size } = raw
  // 仅保留有意义的查询条件，去除空串/null/undefined。
  // 重要：currentRegionCode/status 等字段在 MyBatis 中使用 OGNL 的 !=null 判断，
  // 空字符串会进入分支并以「= ''」参与过滤，把所有数据过滤掉；validFrom/validTo
  // 经 Spring 的 LocalDate 解析失败还会导致 400。
  const next = {
    permitNo: raw.permitNo,
    personName: raw.personName,
    identityNo: raw.identityNo,
    currentRegionCode: raw.currentRegionCode,
    status: raw.status,
    validFrom: raw.validFrom,
    validTo: raw.validTo
  }
  Object.keys(next).forEach((key) => {
    const value = next[key]
    if (value === '' || value === null || value === undefined) delete next[key]
  })
  return request({ url: '/residence-permits', method: 'get', params: toSpringPageParams({ ...next, current, size }) })
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
