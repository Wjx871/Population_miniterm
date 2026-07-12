import request from './request'
import { toSpringPageParams } from '../utils/page'

export function getPersonPage(params) {
  return request({
    url: '/persons',
    method: 'get',
    params: toSpringPageParams(params),
  })
}

export function getPersonById(id) {
  return request({
    url: `/persons/${id}`,
    method: 'get',
  })
}

export function getPersonByIdCard(idCard) {
  return request({
    url: `/persons/id-card/${encodeURIComponent(idCard)}`,
    method: 'get',
  })
}

export function createPerson(data) {
  return request({
    url: '/persons',
    method: 'post',
    data,
  })
}

export function updatePerson(id, data) {
  return request({
    url: `/persons/${id}`,
    method: 'put',
    data,
  })
}

/**
 * 软删除人口（后端将状态置为「已注销」）。
 * M2 页面禁止调用；保留导出供后续审计或审批流程使用。
 */
export function deletePerson(id) {
  return request({
    url: `/persons/${id}`,
    method: 'delete',
  })
}
