import request from './request.js'
import { toSpringPageParams } from '../utils/page.js'

function paramsOf(params = {}) {
  const { dateRange, ...rest } = params
  const normalized = toSpringPageParams(rest)
  if (Array.isArray(dateRange) && dateRange.length === 2) {
    normalized.dateFrom = dateRange[0]
    normalized.dateTo = dateRange[1]
  }
  return Object.fromEntries(Object.entries(normalized).filter(([, value]) => value !== '' && value !== null && value !== undefined))
}

export function getOperationLogs(params = {}) {
  return request({ url: '/logs/operations', method: 'get', params: paramsOf(params) })
}

export function getLoginLogs(params = {}) {
  return request({ url: '/logs/logins', method: 'get', params: paramsOf(params) })
}

export function getOperationLog(id) {
  return request({ url: `/logs/operations/${id}`, method: 'get' })
}
