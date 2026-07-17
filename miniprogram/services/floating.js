const { request } = require('./request')

function list(params) { return request({ url: '/api/floating-populations', data: params }) }
function detail(id) { return request({ url: `/api/floating-populations/${id}` }) }

module.exports = { list, detail }
