/**
 * 【数据大屏链路 · API 定义】
 * 上游：useDashboardData.loadOverview / loadCharts
 * 下游：request.js（baseURL=/api + JWT）→ Vite 代理 → 后端 DashboardController
 * 实际请求：GET /api/dashboard/overview 、GET /api/dashboard/charts
 */
import request from './request'

/** 顶部 KPI、人口结构、重点业务等概览指标 */
export function getDashboardOverview(params = {}) {
  return request({ url: '/dashboard/overview', method: 'get', params })
}

/** 迁入迁出趋势、审批分布、业务量、登记签发趋势等图表数据 */
export function getDashboardCharts(params = {}) {
  return request({ url: '/dashboard/charts', method: 'get', params })
}
