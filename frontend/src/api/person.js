import axios from 'axios';

// 配置基础 axios 实例
const request = axios.create({
  baseURL: '/api/persons',
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
 * 分页查询人口列表
 * @param {Object} params 查询参数 (name, idCard, status, page, size)
 */
export function getPersonList(params) {
  // Spring Data Pageable 期望的 page 是从 0 开始的索引，而 size 是页长
  // 后端接收 Pageable 参数，我们将其进行格式转换
  const requestParams = {
    name: params.name || undefined,
    idCard: params.idCard || undefined,
    status: params.status || undefined,
    page: params.page !== undefined ? params.page : 0,
    size: params.size || 10,
  };
  return request({
    url: '',
    method: 'get',
    params: requestParams,
  }).then(res => res.data);
}

/**
 * 根据 ID 查询人口详情
 * @param {number|string} id 人口 ID
 */
export function getPerson(id) {
  return request({
    url: `/${id}`,
    method: 'get',
  }).then(res => res.data);
}

/**
 * 新增人口基础信息
 * @param {Object} data 人口信息
 */
export function createPerson(data) {
  return request({
    url: '',
    method: 'post',
    data,
  }).then(res => res.data);
}

/**
 * 修改人口基础信息
 * @param {number|string} id 人口 ID
 * @param {Object} data 修改数据
 */
export function updatePerson(id, data) {
  return request({
    url: `/${id}`,
    method: 'put',
    data,
  }).then(res => res.data);
}

/**
 * 注销/删除人口信息
 * @param {number|string} id 人口 ID
 */
export function deletePerson(id) {
  return request({
    url: `/${id}`,
    method: 'delete',
  }).then(res => res.data);
}
