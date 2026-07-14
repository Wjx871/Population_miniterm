import request from './request.js'
import { toSpringPageParams } from '../utils/page.js'

function clean(params = {}) {
  return Object.fromEntries(Object.entries(params).filter(([, value]) => value !== '' && value !== null && value !== undefined))
}

export function queryPersons(params = {}) {
  return request({ url: '/query/persons', method: 'get', params: clean(toSpringPageParams(params)) })
}

export function queryHouseholds(params = {}) {
  return request({ url: '/query/households', method: 'get', params: clean(toSpringPageParams(params)) })
}

export function queryMigrationHistory(params = {}) {
  return request({ url: '/query/migration-history', method: 'get', params: clean(toSpringPageParams(params)) })
}

export function getComprehensivePersonProfile(personId) {
  return request({ url: `/queries/persons/${personId}`, method: 'get' })
}
