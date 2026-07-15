const { request } = require('./request')
function overview() { return request({ url: '/api/dashboard/overview', data: { periodDays: 30, expiryDays: 30 } }) }
function householdTotal() { return request({ url: '/api/households', data: { page: 0, size: 1 } }).then((page) => page.totalElements) }
function processingApplications() {
  return Promise.all(['SUBMITTED', 'UNDER_REVIEW', 'APPROVED'].map((status) => request({
    url: '/api/applications', data: { status, page: 0, size: 1 }
  }))).then((pages) => pages.reduce((total, page) => total + page.totalElements, 0))
}
function health() { return request({ url: '/api/health' }) }
module.exports = { overview, householdTotal, processingApplications, health }
