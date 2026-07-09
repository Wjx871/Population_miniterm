import request from './request'

export function getMigrationPage(params) {
  return request({
    url: '/migrations',
    method: 'get',
    params,
  })
}

export function createMigration(data) {
  return request({
    url: '/migrations',
    method: 'post',
    data,
  })
}

export function deleteMigration(id) {
  return request({
    url: `/migrations/${id}`,
    method: 'delete',
  })
}
