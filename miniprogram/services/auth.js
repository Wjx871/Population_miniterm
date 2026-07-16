const { request } = require('./request')
function login(username, password) { return request({ url: '/api/auth/login', method: 'POST', data: { username, password } }) }
function me() { return request({ url: '/api/auth/me' }) }
function logout() { return request({ url: '/api/auth/logout', method: 'POST' }) }
module.exports = { login, me, logout }
