import request from './request'
import { toSpringPageParams } from '../utils/page'

export function getUserPage(params) {
  return request({
    url: '/users',
    method: 'get',
    params: toSpringPageParams(params),
  })
}
