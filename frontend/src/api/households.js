import request from './request'
import { toSpringPageParams } from '../utils/page'

export function getHouseholdPage(params) {
  return request({
    url: '/households',
    method: 'get',
    params: toSpringPageParams(params),
  })
}

export function getHouseholdList() {
  return request({
    url: '/households/list',
    method: 'get',
  })
}

export function getHouseholdById(id) {
  return request({
    url: `/households/${id}`,
    method: 'get',
  })
}

export function createHousehold(data) {
  return request({
    url: '/households',
    method: 'post',
    data,
  })
}

export function updateHousehold(id, data) {
  return request({
    url: `/households/${id}`,
    method: 'put',
    data,
  })
}

export function deleteHousehold(id) {
  return request({
    url: `/households/${id}`,
    method: 'delete',
  })
}

export function getHouseholdMembers(id) {
  return request({
    url: `/households/${id}/members`,
    method: 'get',
  })
}

export function addHouseholdMember(id, data) {
  return request({
    url: `/households/${id}/members`,
    method: 'post',
    data,
  })
}

export function removeHouseholdMember(householdId, memberId) {
  return request({
    url: `/households/${householdId}/members/${memberId}`,
    method: 'delete',
  })
}
