const { request } = require('./request')
function list(params) { return request({ url: '/api/persons', data: params }) }
function detail(id) { return request({ url: `/api/persons/${id}` }) }
function profile(id) { return request({ url: `/api/queries/persons/${id}` }) }
module.exports = { list, detail, profile }
