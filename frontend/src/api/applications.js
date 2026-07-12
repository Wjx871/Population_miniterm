import request from './request'
import { toSpringPageParams } from '../utils/page'

export function getApplicationPage(params) {
  return request({ url: '/applications', method: 'get', params: toSpringPageParams(params) })
}

export function getApplicationDetail(applicationId) {
  return request({ url: `/applications/${applicationId}`, method: 'get' })
}

export function getApplicationApprovalLogs(applicationId) {
  return request({ url: `/applications/${applicationId}/approval-logs`, method: 'get' })
}

export function submitApplication(applicationId) {
  return request({ url: `/applications/${applicationId}/submit`, method: 'post' })
}

export function withdrawApplication(applicationId) {
  return request({ url: `/applications/${applicationId}/withdraw`, method: 'post' })
}

export function cancelDraftApplication(applicationId) {
  return request({ url: `/applications/${applicationId}`, method: 'delete' })
}
