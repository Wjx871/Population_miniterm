import request from './request';

export function login(data) {
  return request({
    url: '/auth/login',
    method: 'post',
    data,
  });
}

export function getCurrentUser() {
  return request({
    url: '/auth/me',
    method: 'get',
  });
}

export function logout() {
  return request({
    url: '/auth/logout',
    method: 'post',
  });
}
