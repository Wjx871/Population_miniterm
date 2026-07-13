import request from './request'
import { toSpringPageParams } from '../utils/page'

export function getOperationLogs(params) {
  return request({ url: '/logs/operations', method: 'get', params: toSpringPageParams(params) })
}

export function getOperationLogDetail(id) {
  return request({ url: `/logs/operations/${id}`, method: 'get' })
}

export function getLoginLogs(params) {
  return request({ url: '/logs/logins', method: 'get', params: toSpringPageParams(params) })
}
