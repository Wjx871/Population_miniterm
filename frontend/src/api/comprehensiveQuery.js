import request from './request'
import { toSpringPageParams } from '../utils/page'

function trimToUndefined(value) {
  if (value === null || value === undefined) return undefined
  const text = String(value).trim()
  return text || undefined
}

export function getComprehensivePersonPage(params = {}) {
  const normalized = {
    name: trimToUndefined(params.name),
    identityNo: trimToUndefined(params.identityNo),
    gender: trimToUndefined(params.gender),
    currentStatus: trimToUndefined(params.currentStatus),
    regionCode: trimToUndefined(params.regionCode),
    residenceStatus: trimToUndefined(params.residenceStatus),
    floatingStatus: trimToUndefined(params.floatingStatus),
    certificateType: trimToUndefined(params.certificateType),
    keyPopulationType: trimToUndefined(params.keyPopulationType),
    sort: 'personId,DESC',
    current: params.current,
    size: params.size,
  }
  return request({ url: '/query/persons', method: 'get', params: toSpringPageParams(normalized) })
}

export function getComprehensivePersonProfile(personId) {
  return request({ url: `/queries/persons/${personId}`, method: 'get' })
}
