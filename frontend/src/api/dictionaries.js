import request from './request.js'

export function getDictionaryPage(params) {
  return request({ url: '/dictionaries', method: 'get', params })
}

export function getDictionaryItems(type) {
  return request({ url: `/dictionaries/${type}`, method: 'get' })
}

export function getDictionaryDetail(type, code) {
  return request({ url: `/dictionaries/${type}/${code}`, method: 'get' })
}

export function createDictionaryItem(data) {
  return request({ url: '/dictionaries', method: 'post', data })
}

export function updateDictionaryItem(id, data) {
  return request({ url: `/dictionaries/${id}`, method: 'put', data })
}

export function enableDictionaryItem(id, version) {
  return request({ url: `/dictionaries/${id}/enable`, method: 'post', data: { version } })
}

export function disableDictionaryItem(id, version) {
  return request({ url: `/dictionaries/${id}/disable`, method: 'post', data: { version } })
}
