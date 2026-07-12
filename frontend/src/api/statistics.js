import request from './request'

/**
 * 获取大屏核心面板数据汇总
 */
export function getSummary() {
  return request({
    url: '/statistics/summary',
    method: 'get',
  })
}

/**
 * 获取统计图表所需的数据
 */
export function getCharts() {
  return request({
    url: '/statistics/charts',
    method: 'get',
  })
}

/**
 * 获取大屏显示的系统操作日志
 */
export function getLogs() {
  return request({
    url: '/statistics/logs',
    method: 'get',
  })
}
