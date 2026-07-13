import test from 'node:test'
import assert from 'node:assert/strict'

import { createMigrationHandler } from '../src/features/applications/handlers/migrationHandler.js'
import { createFloatingHandler } from '../src/features/applications/handlers/floatingHandler.js'
import { createResidencePermitHandler } from '../src/features/applications/handlers/residencePermitHandler.js'

/**
 * 与 ApprovalDetail.vue 中的解析语义一致：通过 Handler 取专业详情与材料规则，
 * 不再依赖硬编码 migration/floating/permit 多组 ref。
 */
function resolveHandler(handlers, businessType) {
  for (const handler of handlers) {
    if (handler.supports(businessType)) return handler
  }
  return null
}

function resolveProfessionalForApproval(handlers, businessType, rawDetail) {
  const handler = resolveHandler(handlers, businessType)
  if (!handler) return { handler: null, detail: null }
  const detail = typeof handler.normalizeDetail === 'function'
    ? handler.normalizeDetail(rawDetail)
    : rawDetail
  return { handler, detail }
}

function checkMaterialsVerified(handlers, businessType, professionalDetail, materials) {
  const handler = resolveHandler(handlers, businessType)
  if (!handler || !professionalDetail) {
    const required = (materials || []).filter((item) => item.requiredFlag)
    return required.length > 0 && required.every((item) => item.verifyStatus === 'VERIFIED')
  }
  return handler.hasVerifiedMaterials({
    businessType,
    detail: professionalDetail,
    materials
  })
}

const legacyHandlers = [
  createMigrationHandler({
    hasCompleteMigrationMaterials: (dir, type, mats) => {
      return dir === 'in' && type === 'NORMAL' && mats.every((m) => m.verifyStatus === 'VERIFIED')
    }
  }),
  createFloatingHandler({}),
  createResidencePermitHandler({
    hasVerifiedPermitMaterials: (mats, applyType) => applyType === 'ENDORSEMENT'
  })
]

test('审批详情可通过 Handler 解析旧三类业务', () => {
  assert.ok(resolveHandler(legacyHandlers, 'MIGRATION_IN'))
  assert.ok(resolveHandler(legacyHandlers, 'MIGRATION_OUT'))
  assert.ok(resolveHandler(legacyHandlers, 'FLOATING_REGISTRATION'))
  assert.ok(resolveHandler(legacyHandlers, 'RESIDENCE_PERMIT_FIRST_ISSUE'))
})

test('审批详情材料完整性判断经 Handler，不依赖硬编码 family 列表', () => {
  const detail = {
    migrationIn: { migrationType: 'NORMAL', version: 1 },
    application: { businessType: 'MIGRATION_IN' }
  }
  const materials = [
    { materialType: 'ID', verifyStatus: 'VERIFIED', requiredFlag: true }
  ]

  assert.equal(
    checkMaterialsVerified(legacyHandlers, 'MIGRATION_IN', detail, materials),
    true
  )
})

test('审批详情 normalizeDetail 后可读取 display/subject', () => {
  const floating = createFloatingHandler({
    normalizeFloatingProfessional: (raw) => ({
      application: raw.application,
      professional: { ...raw.professional },
      subject: raw.subject,
      materials: raw.materials || [],
      executable: raw.executable
    })
  })
  const raw = {
    application: { businessType: 'FLOATING_REGISTRATION' },
    professional: {
      version: 2,
      businessStatus: 'UNDER_REVIEW',
      residenceReasonCode: 'WORK',
      personName: '李四'
    },
    subject: { personName: '李四', identityNo: '110***********1234' },
    materials: [],
    executable: false
  }

  const normalized = floating.normalizeDetail(raw)
  assert.equal(floating.getDisplayDetail(normalized)?.personName, '李四')
  assert.equal(floating.getSubject(normalized)?.personName, '李四')
  assert.equal(normalized.executable, false)
})

test('resolveProfessionalForApproval 对未知业务返回 null handler', () => {
  const result = resolveProfessionalForApproval(legacyHandlers, 'PERSON_CANCELLATION', { foo: 1 })
  assert.equal(result.handler, null)
  assert.equal(result.detail, null)
})

test('resolveProfessionalForApproval 对迁移业务保留原始结构', () => {
  const raw = {
    migrationIn: { personId: 9, migrationType: 'A', version: 1 },
    application: { businessType: 'MIGRATION_IN' }
  }
  const result = resolveProfessionalForApproval(legacyHandlers, 'MIGRATION_IN', raw)
  assert.ok(result.handler)
  assert.equal(result.handler.family, 'migration')
  assert.equal(result.detail.migrationIn.personId, 9)
})

test('无 Handler 时材料判断走 requiredFlag 回退', () => {
  const materialsOk = [
    { requiredFlag: true, verifyStatus: 'VERIFIED' },
    { requiredFlag: true, verifyStatus: 'VERIFIED' }
  ]
  const materialsBad = [
    { requiredFlag: true, verifyStatus: 'VERIFIED' },
    { requiredFlag: true, verifyStatus: 'PENDING' }
  ]
  assert.equal(checkMaterialsVerified(legacyHandlers, 'UNKNOWN_BIZ', null, materialsOk), true)
  assert.equal(checkMaterialsVerified(legacyHandlers, 'UNKNOWN_BIZ', null, materialsBad), false)
  assert.equal(checkMaterialsVerified(legacyHandlers, 'UNKNOWN_BIZ', null, []), false)
})

test('居住证审批材料核验接收 applyType', () => {
  let receivedApplyType = null
  const permit = createResidencePermitHandler({
    hasVerifiedPermitMaterials: (mats, applyType) => {
      receivedApplyType = applyType
      return true
    }
  })

  permit.hasVerifiedMaterials({
    businessType: 'RESIDENCE_PERMIT_ENDORSEMENT',
    detail: { subject: { residenceReasonCode: 'WORK' }, professional: {} },
    materials: []
  })
  assert.equal(receivedApplyType, 'ENDORSEMENT')
})

test('审批页不依赖硬编码 family 分支即可拿到材料规则文案', () => {
  const migration = createMigrationHandler({
    getMigrationMaterialRuleText: (dir, type) => `${dir}:${type}`
  })
  const text = migration.getMaterialRuleText({
    businessType: 'MIGRATION_OUT',
    detail: { migrationOut: { migrationType: 'BATCH' } }
  })
  assert.equal(text, 'out:BATCH')
})
