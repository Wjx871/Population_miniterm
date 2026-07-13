import test from 'node:test'
import assert from 'node:assert/strict'

import {
  toCreatePersonCancellationPayload,
  toUpdatePersonCancellationPayload,
  toCreateHouseholdCancellationPayload,
  toUpdateHouseholdCancellationPayload,
  toCancellationQueryParams,
  toHouseholdArchiveQueryParams,
  normalizeCancellationProfessional,
  getCancellationDetailFields
} from '../src/adapters/cancellation.js'
import {
  PERSON_CANCELLATION_REASON,
  HOUSEHOLD_CANCELLATION_REASON,
  getCancellationReasonOptions,
  getCancellationMaterialOptions,
  hasVerifiedCancellationMaterials,
  CANCEL_OBJECT_TYPE
} from '../src/constants/cancellation.js'
import { createCancellationHandler } from '../src/features/applications/handlers/cancellationHandler.js'

test('人员注销创建 payload 不含 version 与数据库列名', () => {
  const payload = toCreatePersonCancellationPayload({
    personId: 1,
    cancelReasonCode: 'DEATH',
    cancelReasonDetail: '病故',
    eventDate: '2026-07-01',
    newHeadPersonId: 2,
    title: '死亡注销',
    reason: '办理注销',
    remark: '备注',
    version: 9,
    extra: 'x'
  })
  assert.deepEqual(payload, {
    personId: 1,
    cancelReasonCode: 'DEATH',
    cancelReasonDetail: '病故',
    eventDate: '2026-07-01',
    newHeadPersonId: 2,
    title: '死亡注销',
    reason: '办理注销',
    remark: '备注'
  })
  assert.equal('version' in payload, false)
  assert.equal('extra' in payload, false)
})

test('人员注销更新 payload 仅追加真实 version', () => {
  const payload = toUpdatePersonCancellationPayload({
    personId: 1,
    cancelReasonCode: 'DEATH',
    eventDate: '2026-07-01',
    title: 't',
    reason: 'r',
    version: 3
  })
  assert.equal(payload.version, 3)
  assert.equal(payload.personId, 1)
})

test('家庭户销户创建/更新 payload 分离', () => {
  const createPayload = toCreateHouseholdCancellationPayload({
    householdId: 8,
    cancelReasonCode: 'NO_ACTIVE_MEMBERS',
    eventDate: '2026-07-02',
    title: '销户',
    reason: '无成员',
    version: 1
  })
  assert.equal('version' in createPayload, false)
  assert.equal(createPayload.householdId, 8)

  const updatePayload = toUpdateHouseholdCancellationPayload({
    householdId: 8,
    cancelReasonCode: 'NO_ACTIVE_MEMBERS',
    eventDate: '2026-07-02',
    title: '销户',
    reason: '无成员',
    version: 4
  })
  assert.equal(updatePayload.version, 4)
})

test('注销查询参数仅提交真实字段，不含 keyword/applicationStatus', () => {
  const params = toCancellationQueryParams({
    cancellationNo: 'C1',
    cancelObjectType: 'PERSON',
    cancelReasonCode: 'DEATH',
    personName: '张三',
    identityNo: '110',
    householdNo: 'H1',
    businessStatus: 'APPROVED',
    regionCode: '110000',
    eventFrom: '2026-01-01',
    eventTo: '2026-12-31',
    current: 2,
    size: 20,
    keyword: 'should-not-send',
    applicationStatus: 'DRAFT',
    createdFrom: 'x'
  })
  assert.equal(params.keyword, undefined)
  assert.equal(params.applicationStatus, undefined)
  assert.equal(params.createdFrom, undefined)
  assert.equal(params.cancellationNo, 'C1')
  assert.equal(params.current, 2)
  assert.equal(params.size, 20)
})

test('家庭户归档查询参数冻结真实字段', () => {
  const params = toHouseholdArchiveQueryParams({
    householdNo: 'H9',
    headPersonName: '李四',
    regionCode: '110101',
    reasonCode: 'HOUSEHOLD_MERGED',
    archivedFrom: '2026-07-01T00:00:00',
    archivedTo: '2026-07-13T23:59:59',
    current: 1,
    size: 10,
    keyword: 'nope'
  })
  assert.equal(params.keyword, undefined)
  assert.equal(params.archivedFrom, '2026-07-01T00:00:00')
  assert.equal(params.headPersonName, '李四')
})

test('注销原因按对象类型拆分，不跨类型显示', () => {
  const personReasons = getCancellationReasonOptions('PERSON').map((i) => i.value)
  const householdReasons = getCancellationReasonOptions('HOUSEHOLD').map((i) => i.value)

  assert.ok(personReasons.includes('DEATH'))
  assert.ok(!personReasons.includes('NO_ACTIVE_MEMBERS'))
  assert.ok(householdReasons.includes('HOUSEHOLD_MERGED'))
  assert.ok(!householdReasons.includes('DEATH'))

  assert.ok(Object.keys(PERSON_CANCELLATION_REASON).includes('SETTLED_ABROAD'))
  assert.ok(Object.keys(HOUSEHOLD_CANCELLATION_REASON).includes('ADDRESS_INVALIDATED'))
})

test('材料核验对齐后端人员/家庭户规则', () => {
  const personMaterials = [
    { materialType: 'APPLICANT_IDENTITY_PROOF', verifyStatus: 'VERIFIED' },
    { materialType: 'DEATH_CERTIFICATE', verifyStatus: 'VERIFIED' },
    { materialType: 'HOUSEHOLD_BOOK', verifyStatus: 'VERIFIED' }
  ]
  assert.equal(
    hasVerifiedCancellationMaterials(CANCEL_OBJECT_TYPE.PERSON, 'DEATH', personMaterials),
    true
  )
  assert.equal(
    hasVerifiedCancellationMaterials(CANCEL_OBJECT_TYPE.PERSON, 'DEATH', personMaterials.slice(0, 2)),
    false
  )

  const householdMaterials = [
    { materialType: 'CANCELLATION_APPLICATION', verifyStatus: 'VERIFIED' },
    { materialType: 'HOUSEHOLD_BOOK', verifyStatus: 'VERIFIED' },
    { materialType: 'HOUSEHOLD_MERGE_PROOF', verifyStatus: 'VERIFIED' }
  ]
  assert.equal(
    hasVerifiedCancellationMaterials(CANCEL_OBJECT_TYPE.HOUSEHOLD, 'HOUSEHOLD_MERGED', householdMaterials),
    true
  )
})

test('人员死亡材料选项包含死亡证明与户口簿', () => {
  const options = getCancellationMaterialOptions(CANCEL_OBJECT_TYPE.PERSON, 'DEATH')
  const values = options.map((o) => o.value)
  assert.ok(values.includes('APPLICANT_IDENTITY_PROOF'))
  assert.ok(values.includes('DEATH_CERTIFICATE'))
  assert.ok(values.includes('HOUSEHOLD_BOOK'))
})

test('专业详情规范化映射 executionRestriction 与 executable', () => {
  const normalized = normalizeCancellationProfessional({
    application: { businessType: 'PERSON_CANCELLATION', status: 'APPROVED' },
    cancellation: {
      cancellationId: 1,
      cancelObjectType: 'PERSON',
      cancelReasonCode: 'DEATH',
      businessStatus: 'APPROVED',
      version: 5,
      personName: '王五'
    },
    materials: [],
    executable: true,
    executionRestriction: ''
  })
  assert.equal(normalized.cancellation.version, 5)
  assert.equal(normalized.executable, true)
  assert.equal(normalized.unavailableReason, '')
  const fields = getCancellationDetailFields(normalized)
  assert.ok(fields.some((f) => f.label === '注销原因'))
})

test('Handler execute 仅提交 version，无 DELETE', async () => {
  let personArgs = null
  let householdArgs = null
  const handler = createCancellationHandler({
    executePersonCancellation: async (id, version) => {
      personArgs = { id, version }
    },
    executeHouseholdCancellation: async (id, version) => {
      householdArgs = { id, version }
    }
  })

  await handler.execute({
    businessType: 'PERSON_CANCELLATION',
    applicationId: 11,
    detail: { cancellation: { version: 7 } }
  })
  assert.deepEqual(personArgs, { id: 11, version: 7 })

  await handler.execute({
    businessType: 'HOUSEHOLD_CANCELLATION',
    applicationId: 12,
    detail: { cancellation: { version: 2 } }
  })
  assert.deepEqual(householdArgs, { id: 12, version: 2 })

  assert.equal(typeof handler.submit, 'undefined')
  const meta = handler.getExecutionMeta({
    businessType: 'PERSON_CANCELLATION',
    detail: { cancellation: { version: 7 } }
  })
  assert.equal(meta.mode, 'direct-confirm')
  assert.equal(meta.permission, 'cancellation:execute')
  assert.ok(meta.title)
  assert.ok(meta.message)
})
