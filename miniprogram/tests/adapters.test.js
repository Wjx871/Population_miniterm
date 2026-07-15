const test = require('node:test')
const assert = require('node:assert/strict')
const person = require('../adapters/person')
const household = require('../adapters/household')
const application = require('../adapters/application')
const auth = require('../adapters/auth')

test('人口列表 Adapter 保留脱敏值并中文化状态', () => { const row = person.normalize({ personId: 1, name: '张某', idCard: '110***********001X', status: 'NORMAL' }); assert.equal(row.idCardDisplay, '110***********001X'); assert.equal(row.statusDisplay, '正常') })
test('人口分页 Adapter 区分真实零总数和缺失总数', () => {
  assert.deepEqual(person.normalizePage({ content: [], totalElements: 0, number: 0, last: true }), {
    records: [], total: 0, totalAvailable: true, totalDisplay: '0', number: 0, last: true
  })
  assert.equal(person.normalizePage({ content: [], number: 0, last: true }).totalDisplay, '数量暂不可用')
})
test('家庭户列表 Adapter 中文化正式状态', () => { assert.equal(household.normalize({ status: 'PENDING_CANCELLATION' }).statusDisplay, '待注销'); assert.equal(household.normalize({ status: 'ARCHIVED' }).statusDisplay, '已归档') })
test('申请状态完整中文化', () => { assert.equal(application.normalize({ status: 'UNDER_REVIEW' }).statusDisplay, '审批中'); assert.equal(application.normalize({ status: 'WITHDRAWN' }).statusDisplay, '已撤回') })
test('材料状态和必交标记中文化', () => { const row = application.material({ verifyStatus: 'VERIFIED', requiredFlag: true, materialId: 2 }); assert.equal(row.verifyStatusDisplay, '已核验'); assert.equal(row.requiredDisplay, '必交'); assert.equal(row.viewable, true) })
test('用户 Adapter 中文化数据范围', () => assert.equal(auth.normalizeUser({ username: 'viewer', dataScope: 'DEPARTMENT' }).dataScopeDisplay, '本部门数据'))
