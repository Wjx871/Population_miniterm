import request from './request.js'

export function getRegionTree(config = {}) {
  return request({ url: '/admin-regions/tree', method: 'get', ...config })
}

export function searchRegions(params) {
  return request({ url: '/admin-regions', method: 'get', params })
}

export function getRegionDetail(code) {
  return request({ url: `/admin-regions/${code}`, method: 'get' })
}

export function getRegionChildren(code) {
  return request({ url: `/admin-regions/${code}/children`, method: 'get' })
}

export function createRegion(data) {
  return request({ url: '/admin-regions', method: 'post', data })
}

export function updateRegion(code, data) {
  return request({ url: `/admin-regions/${code}`, method: 'put', data })
}

export function enableRegion(code, version) {
  return request({ url: `/admin-regions/${code}/enable`, method: 'post', data: { version } })
}

export function disableRegion(code, version) {
  return request({ url: `/admin-regions/${code}/disable`, method: 'post', data: { version } })
}
