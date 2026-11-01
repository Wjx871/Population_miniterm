const { request } = require('./request')
function list(params) { return request({ url: '/api/households', data: params }) }
function detail(id) { return request({ url: `/api/households/${id}` }) }
function members(id) { return request({ url: `/api/households/${id}/members` }) }
module.exports = { list, detail, members }
