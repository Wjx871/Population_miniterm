import axios from 'axios';

// 配置基础 axios 实例
const request = axios.create({
  baseURL: '/api/statistics',
  timeout: 5000,
});

// 给请求注入 token
request.interceptors.request.use((config) => {
  const token = localStorage.getItem('token');
  if (token) {
    config.headers['Authorization'] = `Bearer ${token}`;
  }
  return config;
}, (error) => {
  return Promise.reject(error);
});

/**
 * 获取大屏核心面板数据汇总
 */
export function getSummary() {
  return request({
    url: '/summary',
    method: 'get',
  }).then(res => res.data);
}

/**
 * 获取统计图表所需的数据
 */
export function getCharts() {
  return request({
    url: '/charts',
    method: 'get',
  }).then(res => res.data);
}

/**
 * 获取大屏显示的系统操作日志
 */
export function getLogs() {
  return request({
    url: '/logs',
    method: 'get',
  }).then(res => res.data);
}
