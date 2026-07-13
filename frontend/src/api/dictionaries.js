import request from './request'

export function getDictionaryItems(type) {
  return request({
    url: `/dictionaries/${type}`,
    method: 'get',
  })
}
