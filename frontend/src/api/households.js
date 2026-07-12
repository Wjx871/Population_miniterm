/**
 * 家庭户 API。
 * 接口契约待后端确认；当前分支与 origin/develop 均未提供 HouseholdController。
 * 前端保留调用结构供页面与 M3 复用，联调结果以实际后端为准。
 */
import request from './request'
import { toSpringPageParams } from '../utils/page'

export function getHouseholdPage(params) {
  return request({
    url: '/households',
    method: 'get',
    params: toSpringPageParams(params),
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

/**
 * 撤销/删除家庭户。
 * M2 页面禁止调用；销户等重大业务走后续审批流程。
 */
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
