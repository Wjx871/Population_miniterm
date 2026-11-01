const test = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')

const adapter = require('../adapters/mobile-business')
const permitService = require('../services/permit')
const applicationService = require('../services/application')

function source(relative) { return fs.readFileSync(path.resolve(__dirname, relative), 'utf8') }

test('mobile business adapters preserve real totals and defensively mask sensitive values', () => {
  const page = adapter.permitPage({ content: [{ permitId: 1, permitNo: 'JZ123456789', identityNo: '110101199001011234', status: 'ACTIVE' }], totalElements: 1, number: 0, last: true })
  assert.equal(page.total, 1)
  assert.equal(page.totalDisplay, '1')
  assert.equal(page.records[0].permitNoDisplay.endsWith('6789'), true)
  assert.equal(page.records[0].permitNoDisplay.includes('*'), true)
  assert.equal(page.records[0].identityNoDisplay.endsWith('1234'), true)
  assert.equal(adapter.floatingPage({ content: [] }).totalDisplay, '数量暂不可用')
})

test('new mobile services use only formal query and lightweight application endpoints', () => {
  const permitSource = source('../services/permit.js')
  const floatingSource = source('../services/floating.js')
  const migrationSource = source('../services/migration.js')
  assert.match(permitSource, /\/api\/residence-permits/)
  assert.match(floatingSource, /\/api\/floating-populations/)
  assert.match(migrationSource, /\/api\/query\/migration-history/)
  assert.doesNotMatch(`${permitSource}\n${source('../pages/permits/endorsement/index.js')}`, /\/endorse[`'"]/)
})

test('new mobile workbench icons resolve to reviewed local Tabler resources', () => {
  const { resolveIcon } = require('../utils/icons')
  for (const name of ['residence-permit', 'migration-in', 'migration-out', 'permission', 'material', 'history', 'check-circle']) {
    assert.equal(resolveIcon({ name }).renderable, true, name)
  }
})

let definition
let redirects
let toasts
global.Page = (value) => { definition = value }
global.getApp = () => ({ globalData: { user: { permissions: ['residence-permit:apply', 'residence-permit:expiry:view'] } } })
global.wx = {
  stopPullDownRefresh() {},
  showToast(options) { toasts.push(options.title) },
  redirectTo(options) { redirects.push(options.url) },
  chooseMessageFile() {}
}

const originalPermit = {
  expiring: permitService.expiring,
  detail: permitService.detail,
  createEndorsement: permitService.createEndorsement,
  uploadMaterial: permitService.uploadMaterial
}
const originalSubmit = applicationService.submit
require('../pages/permits/endorsement/index')

function page() {
  redirects = []
  toasts = []
  return Object.assign({}, definition, {
    data: Object.assign({}, definition.data, { materials: definition.data.materials.map((item) => Object.assign({}, item)) }),
    setData(update) { Object.assign(this.data, update) }
  })
}

test('endorsement first load requests eligible permits once', async () => {
  let requests = 0
  permitService.expiring = async () => { requests += 1; return [{ permitId: 9, permitNo: 'P123456', status: 'ACTIVE' }] }
  const instance = page()
  await instance.onLoad({})
  await instance.onLoad({})
  assert.equal(requests, 1)
  assert.equal(instance.data.selected.permitId, 9)
})

test('endorsement creates one cleaned draft with the selected real permit', async () => {
  let call
  permitService.createEndorsement = async (id, payload) => { call = { id, payload }; return { applicationId: 33, applicationNo: 'RQ-33' } }
  const instance = page()
  instance.setData({ selected: { permitId: 9 }, title: ' 居住证签注 ', reason: ' 持续居住 ', remark: '' })
  await instance.createApplication()
  await instance.createApplication()
  assert.deepEqual(call, { id: 9, payload: { title: '居住证签注', reason: '持续居住', residenceBasisCode: 'EMPLOYMENT' } })
  assert.equal(instance.data.applicationId, 33)
  assert.equal(instance.data.step, 3)
})

test('endorsement requires both materials and handles submit conflict without looping', async () => {
  let submits = 0
  applicationService.submit = async () => { submits += 1; throw { statusCode: 409, message: '申请状态已变化' } }
  const instance = page()
  instance.setData({ applicationId: 33, materials: instance.data.materials.map((item) => Object.assign({}, item, { uploaded: true })) })
  await instance.submit()
  assert.equal(submits, 1)
  assert.deepEqual(redirects, ['/pages/applications/detail/index?id=33'])
  assert.equal(instance.data.submitting, false)
})

test.after(() => {
  Object.assign(permitService, originalPermit)
  applicationService.submit = originalSubmit
})
