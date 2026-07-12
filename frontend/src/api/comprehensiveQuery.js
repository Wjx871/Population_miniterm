import request from './request'
import { toSpringPageParams } from '../utils/page'

function trimToUndefined(value) {
  if (value === null || value === undefined) return undefined
  const text = String(value).trim()
  return text || undefined
}

export function getComprehensivePersonPage(params = {}) {
  const normalized = {
    keyword: trimToUndefined(params.keyword),
    personStatus: trimToUndefined(params.personStatus),
    regionCode: trimToUndefined(params.regionCode),
    residenceStatus: trimToUndefined(params.residenceStatus),
    floatingStatus: trimToUndefined(params.floatingStatus),
    permitStatus: trimToUndefined(params.permitStatus),
    sort: 'personId,DESC',
    current: params.current,
    size: params.size,
  }
  return request({ url: '/queries/persons', method: 'get', params: toSpringPageParams(normalized) })
}

export function getComprehensivePersonProfile(personId) {
  return request({ url: `/queries/persons/${personId}`, method: 'get' })
}
