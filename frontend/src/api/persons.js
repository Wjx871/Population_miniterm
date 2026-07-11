import request from './request'
import { toSpringPageParams } from '../utils/page'

export function getPersonPage(params) {
  return request({
    url: '/persons',
    method: 'get',
    params: toSpringPageParams(params),
  })
}

export function getPersonList(params) {
  return request({
    url: '/persons/list',
    method: 'get',
    params,
  })
}

export function getPersonById(id) {
  return request({
    url: `/persons/${id}`,
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

export function deletePerson(id) {
  return request({
    url: `/persons/${id}`,
    method: 'delete',
  })
}

export function getPersonStatistics() {
  return request({
    url: '/persons/statistics',
    method: 'get',
  })
}
