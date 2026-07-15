const { request } = require('./request')

function list(params) { return request({ url: '/api/query/migration-history', data: params }) }

module.exports = { list }
