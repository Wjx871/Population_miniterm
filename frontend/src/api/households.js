import request from './request.js'
import { toSpringPageParams } from '../utils/page.js'

export function getHouseholdPage(params) {
  return request({ url: '/households', method: 'get', params: toSpringPageParams(params) })
}

export function getHouseholdById(id) {
  return request({ url: `/households/${id}`, method: 'get' })
}

export function createHousehold(data) {
  return request({ url: '/households', method: 'post', data })
}

export function updateHousehold(id, data) {
  return request({ url: `/households/${id}`, method: 'put', data })
}

export function getHouseholdMembers(id) {
  return request({ url: `/households/${id}/members`, method: 'get' })
}

export function addHouseholdMember(id, data) {
  return request({ url: `/households/${id}/members`, method: 'post', data })
}

export function updateHouseholdMember(householdId, memberId, data) {
  return request({ url: `/households/${householdId}/members/${memberId}`, method: 'put', data })
}

export function leaveHouseholdMember(householdId, memberId, data) {
  return request({ url: `/households/${householdId}/members/${memberId}/leave`, method: 'post', data })
}

export function changeHouseholdHead(householdId, data) {
  return request({ url: `/households/${householdId}/change-head`, method: 'post', data })
}
