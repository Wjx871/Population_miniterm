import request from './request'

export function getDashboardOverview(params = {}) {
  return request({ url: '/dashboard/overview', method: 'get', params })
}

export function getDashboardCharts(params = {}) {
  return request({ url: '/dashboard/charts', method: 'get', params })
}
