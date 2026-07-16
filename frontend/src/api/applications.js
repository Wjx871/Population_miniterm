import request from './request'
import { toSpringPageParams } from '../utils/page'

export function getApplicationPage(params) {
  return request({ url: '/applications', method: 'get', params: toSpringPageParams(params) })
}

export function createApplication(payload) {
  return request({ url: '/applications', method: 'post', data: payload })
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

/**
 * 执行人 / 复核岗退回已批准申请，区别于审批人驳回：
 *   - 退回不改写审批结论，而是让申请回到申请人侧进行补正或撤回；
 *   - 后端权限码 application:return（HOUSEHOLD_MANAGER、SYSTEM_ADMIN 持有）。
 */
export function returnApplication(applicationId, payload) {
  return request({ url: `/applications/${applicationId}/return`, method: 'post', data: payload })
}

export function cancelDraftApplication(applicationId) {
  return request({ url: `/applications/${applicationId}`, method: 'delete' })
}
