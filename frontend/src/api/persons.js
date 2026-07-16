import request from './request'
import { toSpringPageParams } from '../utils/page'

export function getPersonPage(params) {
  return request({
    url: '/persons',
    method: 'get',
    params: toSpringPageParams(params),
  })
}

export function getPersonById(id) {
  return request({
    url: `/persons/${id}`,
    method: 'get',
  })
}

export function getPersonByIdCard(idCard) {
  return request({
    url: `/persons/id-card/${encodeURIComponent(idCard)}`,
    method: 'get',
  })
}

export function createPerson(data) {
  return request({
    url: '/persons',
    method: 'post',
    data,
  })
}

export function updatePerson(id, data) {
  return request({
    url: `/persons/${id}`,
    method: 'put',
    data,
  })
}

/**
 * 上传身份证影印本（用作新增人口前的必传材料）。
 * @param {FormData} formData formData.append('file', blob, 'idcard.jpg')
 * @param {object} options { skipOcr?: boolean, silent?: boolean }
 */
export function uploadIdCardImage(formData, options = {}) {
  return request({
    url: '/persons/idcard-image',
    method: 'post',
    data: formData,
    params: options.skipOcr ? { skipOcr: 'true' } : undefined,
    silent: options.silent,
  })
}

export function getIdCardImage(id, options = {}) {
  return request({
    url: `/persons/idcard-image/${id}`,
    method: 'get',
    silent: options.silent,
  })
}
