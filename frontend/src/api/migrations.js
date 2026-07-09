import request from './request'

export function getMigrationInPage(params) {
  return request({
    url: '/migrations/in',
    method: 'get',
    params,
  })
}

export function createMigrationIn(data) {
  return request({
    url: '/migrations/in',
    method: 'post',
    data,
  })
}

export function deleteMigrationIn(id) {
  return request({
    url: `/migrations/in/${id}`,
    method: 'delete',
  })
}

export function getMigrationOutPage(params) {
  return request({
    url: '/migrations/out',
    method: 'get',
    params,
  })
}

export function createMigrationOut(data) {
  return request({
    url: '/migrations/out',
    method: 'post',
    data,
  })
}

export function deleteMigrationOut(id) {
  return request({
    url: `/migrations/out/${id}`,
    method: 'delete',
  })
}
