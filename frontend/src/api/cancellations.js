import request from './request.js'
import { toSpringPageParams } from '../utils/page.js'

export function createPersonCancellation(payload) {
  return request({ url: '/cancellations/person/applications', method: 'post', data: payload })
}

export function updatePersonCancellation(applicationId, payload) {
  return request({ url: `/cancellations/person/applications/${applicationId}`, method: 'put', data: payload })
}

export function createHouseholdCancellation(payload) {
  return request({ url: '/cancellations/household/applications', method: 'post', data: payload })
}

export function updateHouseholdCancellation(applicationId, payload) {
  return request({ url: `/cancellations/household/applications/${applicationId}`, method: 'put', data: payload })
}

export function getCancellationApplicationDetail(applicationId) {
  return request({ url: `/cancellations/applications/${applicationId}`, method: 'get' })
}

export const getCancellationApplication = getCancellationApplicationDetail

export function executePersonCancellation(applicationId, version) {
  return request({url:`/cancellations/person/applications/${applicationId}/execute`,method:'post',data:{version}})
}

export function executeHouseholdCancellation(applicationId, version) {
  return request({url:`/cancellations/household/applications/${applicationId}/execute`,method:'post',data:{version}})
}

export function getCancellationPage(params) {
  return request({ url: '/cancellations', method: 'get', params: toSpringPageParams(params) })
}

export function getHouseholdArchivePage(params) {
  return request({ url: '/household-archives', method: 'get', params: toSpringPageParams(params) })
}

export function getHouseholdArchiveDetail(archiveId) {
  return request({ url: `/household-archives/${archiveId}`, method: 'get' })
}
