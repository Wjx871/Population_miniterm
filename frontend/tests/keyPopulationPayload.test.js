import test from 'node:test'
import assert from 'node:assert/strict'

import {
  toCreateRegisterPayload,
  toCreateReleasePayload,
  toKeyPopulationQueryParams,
  normalizeKeyPopulationApplication
} from '../src/adapters/keyPopulation.js'
import { hasVerifiedKeyPopulationMaterials } from '../src/constants/keyPopulation.js'
import { createKeyPopulationHandler } from '../src/features/applications/handlers/keyPopulationHandler.js'

test('建档 payload 字段严格匹配 RegisterApplicationRequest', () => {
  const payload = toCreateRegisterPayload({
    personId: 1,
    populationType: 'TYPE_A',
    attentionLevel: 'HIGH',
    registerReason: '原因',
    registerDate: '2026-07-01',
    responsibleDepartmentId: 3,
    responsibleUserId: 4,
    title: '建档',
    remark: '备注',
    version: 9
  })
  assert.deepEqual(payload, {
    personId: 1,
    populationType: 'TYPE_A',
    attentionLevel: 'HIGH',
    registerReason: '原因',
    registerDate: '2026-07-01',
    responsibleDepartmentId: 3,
    responsibleUserId: 4,
    title: '建档',
    remark: '备注'
  })
  assert.equal('version' in payload, false)
})

test('解除 payload 不含 recordId（路径参数）', () => {
  const payload = toCreateReleasePayload({
    releaseReason: '解除',
    releaseDate: '2026-07-10',
    title: '解除申请',
    remark: null
  })
  assert.equal(payload.releaseReason, '解除')
  assert.equal(payload.remark, null)
  assert.equal('recordId' in payload, false)
})

test('查询参数冻结真实筛选字段', () => {
  const params = toKeyPopulationQueryParams({
    personName: '张',
    idCard: '110',
    populationType: 'A',
    attentionLevel: 'LOW',
    status: 'ACTIVE',
    regionCode: '110000',
    registerDateFrom: '2026-01-01',
    registerDateTo: '2026-12-31',
    current: 1,
    size: 10,
    keyword: 'no'
  })
  assert.equal(params.keyword, undefined)
  assert.equal(params.personName, '张')
  assert.equal(params.idCard, '110')
})

test('专业详情规范化嵌套 KeyApplicationView', () => {
  const normalized = normalizeKeyPopulationApplication({
    application: {
      application: { applicationId: 8, businessType: 'KEY_POPULATION_REGISTER', status: 'APPROVED' },
      detailId: 1,
      operationType: 'REGISTER',
      personId: 2,
      populationType: 'A',
      attentionLevel: 'HIGH',
      reason: 'r',
      eventDate: '2026-07-01',
      businessStatus: 'APPROVED',
      version: 4,
      executable: true
    },
    materials: [],
    approvalLogs: []
  })
  assert.equal(normalized.application.applicationId, 8)
  assert.equal(normalized.professional.version, 4)
  assert.equal(normalized.executable, true)
})

test('材料核验要求 KEY_POPULATION_BASIS + SITUATION_DESCRIPTION', () => {
  assert.equal(hasVerifiedKeyPopulationMaterials([]), false)
  assert.equal(
    hasVerifiedKeyPopulationMaterials([
      { materialType: 'KEY_POPULATION_BASIS', verifyStatus: 'VERIFIED' },
      { materialType: 'SITUATION_DESCRIPTION', verifyStatus: 'VERIFIED' }
    ]),
    true
  )
})

test('Handler 使用专业 submit，无编辑路由，execute 仅 version', async () => {
  let submitType = null
  let executeArgs = null
  const handler = createKeyPopulationHandler({
    submitRegisterApplication: async (id) => {
      submitType = `register:${id}`
    },
    submitReleaseApplication: async (id) => {
      submitType = `release:${id}`
    },
    executeRegisterApplication: async (id, version) => {
      executeArgs = { id, version, kind: 'register' }
    },
    executeReleaseApplication: async (id, version) => {
      executeArgs = { id, version, kind: 'release' }
    }
  })

  assert.equal(typeof handler.submit, 'function')
  assert.equal(handler.buildEditRoute(), null)

  await handler.submit({ businessType: 'KEY_POPULATION_REGISTER', applicationId: 11 })
  assert.equal(submitType, 'register:11')
  await handler.submit({ businessType: 'KEY_POPULATION_RELEASE', applicationId: 12 })
  assert.equal(submitType, 'release:12')

  await handler.execute({
    businessType: 'KEY_POPULATION_REGISTER',
    applicationId: 11,
    detail: { professional: { version: 5 } }
  })
  assert.deepEqual(executeArgs, { id: 11, version: 5, kind: 'register' })

  const meta = handler.getExecutionMeta({
    businessType: 'KEY_POPULATION_RELEASE',
    detail: { professional: { version: 2 } }
  })
  assert.equal(meta.permission, 'key-population:execute')
  assert.ok(meta.title)
})
