import request from './request'

export function getUserPage(params) {
  return request({
    url: '/users',
    method: 'get',
    params,
  })
}

export function createUser(data) {
  return request({
    url: '/users',
    method: 'post',
    data,
  })
}

export function updateUser(id, data) {
  return request({
    url: `/users/${id}`,
    method: 'put',
    data,
  })
}

export function deleteUser(id) {
  return request({
    url: `/users/${id}`,
    method: 'delete',
  })
}

// 可选：重置密码接口
export function resetPassword(id) {
  return request({
    url: `/users/${id}/password/reset`,
    method: 'put',
  })
}
