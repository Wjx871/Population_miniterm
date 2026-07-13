import request from './request.js';

export function login(data) {
  return request({
    url: '/auth/login',
    method: 'post',
    data,
  });
}
