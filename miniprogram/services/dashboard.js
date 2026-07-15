const { request } = require('./request')
function overview() { return request({ url: '/api/dashboard/overview', data: { periodDays: 30, expiryDays: 30 } }) }
function householdTotal() { return request({ url: '/api/households', data: { page: 0, size: 1 } }).then((page) => page.totalElements) }
function health() { return request({ url: '/api/health' }) }
module.exports = { overview, householdTotal, health }
