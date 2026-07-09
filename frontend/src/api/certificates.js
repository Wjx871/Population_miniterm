import request from './request'

export function getCertificatePage(params) {
  return request({
    url: '/certificates',
    method: 'get',
    params,
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

export function deleteCertificate(id) {
  return request({
    url: `/certificates/${id}`,
    method: 'delete',
  })
}
