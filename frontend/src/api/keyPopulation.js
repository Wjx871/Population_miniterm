import request from './request'
import { toSpringPageParams } from '../utils/page'

export function getKeyPopulationPage(params) {
  return request({ url: '/key-populations', method: 'get', params: toSpringPageParams(params) })
}

export function getKeyPopulationDetail(recordId) {
  return request({ url: `/key-populations/${recordId}`, method: 'get' })
}

export function getKeyPopulationHistory(recordId) {
  return request({ url: `/key-populations/${recordId}/history`, method: 'get' })
}

export function getPersonKeyPopulations(personId, params = {}) {
  return request({
    url: `/persons/${personId}/key-populations`,
    method: 'get',
    params: toSpringPageParams(params)
  })
}

export function createRegisterApplication(payload) {
  return request({ url: '/key-populations/register-applications', method: 'post', data: payload })
}

export function getRegisterApplicationDetail(applicationId) {
  return request({ url: `/key-populations/register-applications/${applicationId}`, method: 'get' })
}

export function submitRegisterApplication(applicationId) {
  return request({ url: `/key-populations/register-applications/${applicationId}/submit`, method: 'post' })
}

export function executeRegisterApplication(applicationId, version) {
  return request({
    url: `/key-populations/register-applications/${applicationId}/execute`,
    method: 'post',
    data: { version }
  })
}

export function createReleaseApplication(recordId, payload) {
  return request({
    url: `/key-populations/${recordId}/release-applications`,
    method: 'post',
    data: payload
  })
}

export function getReleaseApplicationDetail(applicationId) {
  return request({ url: `/key-populations/release-applications/${applicationId}`, method: 'get' })
}

export function submitReleaseApplication(applicationId) {
  return request({ url: `/key-populations/release-applications/${applicationId}/submit`, method: 'post' })
}

export function executeReleaseApplication(applicationId, version) {
  return request({
    url: `/key-populations/release-applications/${applicationId}/execute`,
    method: 'post',
    data: { version }
  })
}
