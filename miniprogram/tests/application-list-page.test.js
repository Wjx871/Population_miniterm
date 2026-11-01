const test = require('node:test')
const assert = require('node:assert/strict')

let pageDefinition
let requestCount = 0
let requestFailure = false

global.Page = (definition) => { pageDefinition = definition }
global.getApp = () => ({ globalData: { user: { permissions: ['application:view'] } } })
global.wx = {
  request(options) {
    requestCount += 1
    if (requestFailure) { options.fail({ errMsg: 'network error' }); return }
    options.success({
      statusCode: 200,
      data: { code: 200, data: { content: [], number: 0, totalElements: 0, last: true } }
    })
  },
  getStorageSync() { return '' },
  showToast() {},
  reLaunch() {},
  stopPullDownRefresh() {},
  navigateTo() {}
}

require('../pages/applications/list/index')

function createPage() {
  return Object.assign({}, pageDefinition, {
    data: Object.assign({}, pageDefinition.data),
    setData(values) { Object.assign(this.data, values) }
  })
}

test('application list initial lifecycle starts only one request', async () => {
  requestCount = 0
  requestFailure = false
  const page = createPage()
  const firstLoad = page.onLoad()
  const duplicateLoad = page.onLoad()
  await Promise.all([firstLoad, duplicateLoad])
  assert.equal(requestCount, 1)
})

test('application list retries only after a user action', async () => {
  requestCount = 0
  requestFailure = true
  const page = createPage()
  await page.onLoad()
  await page.onReachBottom()
  assert.equal(requestCount, 1)
  await page.search()
  assert.equal(requestCount, 2)
})
