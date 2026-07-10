import request from './request'
export const listPending = () => request({ url: '/approvals/pending', method: 'get' })
export const listProcessed = () => request({ url: '/approvals/processed', method: 'get' })
export const getApproval = (id) => request({ url: `/approvals/${id}`, method: 'get' })
export const approve = (id, data) => request({ url: `/approvals/${id}/approve`, method: 'post', data })
export const reject = (id, data) => request({ url: `/approvals/${id}/reject`, method: 'post', data })
