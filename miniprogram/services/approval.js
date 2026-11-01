const { request } = require('./request')
function pending() { return request({ url: '/api/approvals/pending' }) }
function processed() { return request({ url: '/api/approvals/processed' }) }
function detail(id) { return request({ url: `/api/approvals/${id}` }) }
function approve(id, version, comment) { return request({ url: `/api/approvals/${id}/approve`, method: 'POST', data: { version, comment } }) }
function reject(id, version, comment) { return request({ url: `/api/approvals/${id}/reject`, method: 'POST', data: { version, comment } }) }
module.exports = { pending, processed, detail, approve, reject }
