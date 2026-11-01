const test = require('node:test')
const assert = require('node:assert/strict')

const requests = []
global.wx = {
  request(options) {
    requests.push(options)
    options.success({ statusCode: 200, data: { code: 200, data: {} } })
  },
  getStorageSync() { return '' }
}

const applicationApi = require('../services/application')

test('application list removes empty query values and preserves pagination', async () => {
  await applicationApi.list({ applicationNo: undefined, status: null, keyword: '', page: 0, size: 10 })
  assert.deepEqual(requests.at(-1).data, { page: 0, size: 10 })
})

test('application list passes populated query values unchanged', async () => {
  await applicationApi.list({ applicationNo: 'APP-001', status: 'DRAFT', page: 2, size: 20 })
  assert.deepEqual(requests.at(-1).data, { applicationNo: 'APP-001', status: 'DRAFT', page: 2, size: 20 })
})
