import request from './request'
import { toSpringPageParams } from '../utils/page'

export function createMigrationInApplication(payload) {
  return request({ url: '/migrations/in/applications', method: 'post', data: payload })
}

export function updateMigrationInApplication(applicationId, payload) {
  return request({ url: `/migrations/in/applications/${applicationId}`, method: 'put', data: payload })
}

export function createMigrationOutApplication(payload) {
  return request({ url: '/migrations/out/applications', method: 'post', data: payload })
}

export function updateMigrationOutApplication(applicationId, payload) {
  return request({ url: `/migrations/out/applications/${applicationId}`, method: 'put', data: payload })
}

export function getMigrationApplicationDetail(applicationId) {
  return request({ url: `/migrations/applications/${applicationId}`, method: 'get' })
}

export function executeMigrationIn(applicationId, version) {
  return request({ url: `/migrations/in/applications/${applicationId}/execute`, method: 'post', data: { version } })
}

export function executeMigrationOut(applicationId, version) {
  return request({ url: `/migrations/out/applications/${applicationId}/execute`, method: 'post', data: { version } })
}

export function getResidenceArchivePage(params) {
  return request({ url: '/residence-archives', method: 'get', params: toSpringPageParams(params) })
}

export function getResidenceArchiveDetail(archiveId) {
  return request({ url: `/residence-archives/${archiveId}`, method: 'get' })
}
