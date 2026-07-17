import request from './request'

export function getPendingApprovals() {
  return request({ url: '/approvals/pending', method: 'get' })
}

export function getProcessedApprovals() {
  return request({ url: '/approvals/processed', method: 'get' })
}

export function getApprovalDetail(approvalId) {
  return request({ url: `/approvals/${approvalId}`, method: 'get' })
}

export function approveApproval(approvalId, payload) {
  return request({ url: `/approvals/${approvalId}/approve`, method: 'post', data: payload })
}

export function approveAndCreatePerson(approvalId, payload) {
  return request({ url: `/approvals/${approvalId}/approve-and-create-person`, method: 'post', data: payload })
}

export function rejectApproval(approvalId, payload) {
  return request({ url: `/approvals/${approvalId}/reject`, method: 'post', data: payload })
}
