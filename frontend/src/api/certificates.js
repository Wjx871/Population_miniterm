import request from './request'
import { toSpringPageParams } from '../utils/page'

export function getCertificatePage(params) {
  return request({
    url: '/certificates',
    method: 'get',
    params: toSpringPageParams(params),
  })
}

export function getCertificateById(id) {
  return request({
    url: `/certificates/${id}`,
    method: 'get',
  })
}

export function getPersonCertificates(personId, params) {
  return request({
    url: `/persons/${personId}/certificates`,
    method: 'get',
    params: toSpringPageParams(params),
  })
}

export function createCertificate(data) {
  return request({
    url: '/certificates',
    method: 'post',
    data,
  })
}

export function updateCertificate(id, data) {
  return request({
    url: `/certificates/${id}`,
    method: 'put',
    data,
  })
}

export function cancelCertificate(id, data) {
  return request({
    url: `/certificates/${id}/cancel`,
    method: 'post',
    data,
  })
}
