import test from 'node:test'
import assert from 'node:assert/strict'

import { createMigrationHandler } from '../src/features/applications/handlers/migrationHandler.js'
import { createFloatingHandler } from '../src/features/applications/handlers/floatingHandler.js'
import { createResidencePermitHandler } from '../src/features/applications/handlers/residencePermitHandler.js'

/**
 * 纯函数分发：与 handlers/index.js 的 getApplicationBusinessHandler 同语义，
 * 避免在 Node 测试中加载 Vite 风格无扩展名 API 模块。
 */
function resolveHandler(handlers, businessType) {
  for (const handler of handlers) {
    if (handler.supports(businessType)) return handler
  }
  return null
}

const REQUIRED_METHODS = [
  'supports',
  'loadDetail',
  'getDisplayDetail',
  'getSubject',
  'buildEditRoute',
  'getEditPermission',
  'getMaterialOptions',
  'getMaterialRuleText',
  'hasVerifiedMaterials',
  'getExecutionMeta',
  'execute',
  'isCompleted'
]

function assertHandlerContract(handler, label) {
  for (const method of REQUIRED_METHODS) {
    assert.equal(typeof handler[method], 'function', `${label} 必须实现 ${method}`)
  }
  assert.ok(handler.family, `${label} 必须有 family`)
}

const legacyHandlers = [
  createMigrationHandler({}),
  createFloatingHandler({}),
  createResidencePermitHandler({})
]

test('旧三类 Handler 契约完整', () => {
  for (const handler of legacyHandlers) {
    assertHandlerContract(handler, handler.family)
  }
  assert.equal(legacyHandlers[0].family, 'migration')
  assert.equal(legacyHandlers[1].family, 'floating')
  assert.equal(legacyHandlers[2].family, 'permit')
})

test('旧三类 supports 识别正确，未知业务返回 false', () => {
  const [migration, floating, permit] = legacyHandlers

  assert.equal(migration.supports('MIGRATION_IN'), true)
  assert.equal(migration.supports('MIGRATION_OUT'), true)
  assert.equal(migration.supports('FLOATING_REGISTRATION'), false)

  assert.equal(floating.supports('FLOATING_REGISTRATION'), true)
  assert.equal(floating.supports('MIGRATION_IN'), false)

  assert.equal(permit.supports('RESIDENCE_PERMIT_FIRST_ISSUE'), true)
  assert.equal(permit.supports('RESIDENCE_PERMIT_ENDORSEMENT'), true)
  assert.equal(permit.supports('RESIDENCE_PERMIT_CANCELLATION'), true)
  assert.equal(permit.supports('PERSON_CANCELLATION'), false)
})

test('未知业务类型在注册表中返回 null（仅旧三类时）', () => {
  assert.ok(resolveHandler(legacyHandlers, 'MIGRATION_IN'))
  assert.ok(resolveHandler(legacyHandlers, 'FLOATING_REGISTRATION'))
  assert.ok(resolveHandler(legacyHandlers, 'RESIDENCE_PERMIT_FIRST_ISSUE'))
  assert.equal(resolveHandler(legacyHandlers, 'PERSON_CANCELLATION'), null)
  assert.equal(resolveHandler(legacyHandlers, 'SENSITIVE_DATA_EXPORT'), null)
  assert.equal(resolveHandler(legacyHandlers, 'KEY_POPULATION_REGISTER'), null)
  assert.equal(resolveHandler(legacyHandlers, 'UNKNOWN_TYPE'), null)
})

test('Handler 可选 submit：旧三类无 submit，调用方应回退通用 submit', () => {
  for (const handler of legacyHandlers) {
    assert.equal(typeof handler.submit, 'undefined')
  }
})

test('direct-confirm 执行文案来自 Handler，不再写死迁移', () => {
  const migration = createMigrationHandler({})
  const metaIn = migration.getExecutionMeta({
    businessType: 'MIGRATION_IN',
    detail: { migrationIn: { version: 2 } }
  })
  const metaOut = migration.getExecutionMeta({
    businessType: 'MIGRATION_OUT',
    detail: { migrationOut: { version: 3 } }
  })

  assert.equal(metaIn.mode, 'direct-confirm')
  assert.equal(metaIn.permission, 'migration:execute')
  assert.equal(metaIn.version, 2)
  assert.ok(metaIn.title, '必须提供 title')
  assert.ok(metaIn.message, '必须提供 message')
  assert.match(metaIn.title, /迁入|迁出|迁移/)

  assert.equal(metaOut.mode, 'direct-confirm')
  assert.equal(metaOut.version, 3)
  assert.ok(metaOut.title)
  assert.ok(metaOut.message)
})

test('dialog 模式执行 meta 包含 dialogType 与 title', () => {
  const floating = createFloatingHandler({})
  const permit = createResidencePermitHandler({})

  const floatingMeta = floating.getExecutionMeta({
    detail: { professional: { version: 1 } }
  })
  assert.equal(floatingMeta.mode, 'dialog')
  assert.equal(floatingMeta.dialogType, 'FLOATING_EXECUTE')
  assert.ok(floatingMeta.title)

  const issueMeta = permit.getExecutionMeta({
    businessType: 'RESIDENCE_PERMIT_FIRST_ISSUE',
    detail: { professional: { version: 4 } }
  })
  assert.equal(issueMeta.mode, 'dialog')
  assert.equal(issueMeta.dialogType, 'PERMIT_ISSUE')
  assert.equal(issueMeta.version, 4)
  assert.ok(issueMeta.title)
})

test('专业详情规范化不依赖页面硬编码 family 分支', () => {
  const floating = createFloatingHandler({
    normalizeFloatingProfessional: (raw) => ({
      application: raw.application,
      professional: { ...raw.professional },
      subject: raw.subject,
      executable: raw.executable
    })
  })
  const permit = createResidencePermitHandler({})

  assert.equal(typeof floating.normalizeDetail, 'function')
  assert.equal(typeof permit.normalizeDetail, 'function')

  const raw = {
    application: { businessType: 'FLOATING_REGISTRATION' },
    professional: { version: 7, businessStatus: 'DRAFT', residenceReasonCode: 'WORK' },
    subject: { personName: '张三' },
    executable: true
  }
  const normalized = floating.normalizeDetail(raw)
  assert.equal(normalized.professional.version, 7)
  assert.equal(normalized.executable, true)
})

test('材料上传条件依据 materialOptions 而非 family===migration', () => {
  const migration = createMigrationHandler({
    getMigrationMaterialOptions: () => [{ code: 'ID_CARD', name: '身份证' }]
  })
  const floating = createFloatingHandler({
    getFloatingMaterialOptions: () => [{ code: 'RESIDENCE_PROOF', name: '居住证明' }]
  })
  const emptyMigration = createMigrationHandler({})

  const migrationOptions = migration.getMaterialOptions({
    businessType: 'MIGRATION_IN',
    detail: { migrationIn: { migrationType: 'A' } }
  })
  const floatingOptions = floating.getMaterialOptions({
    detail: { professional: { residenceReasonCode: 'WORK' } }
  })
  const emptyOptions = emptyMigration.getMaterialOptions({
    businessType: 'MIGRATION_IN',
    detail: { migrationIn: {} }
  })

  assert.ok(migrationOptions.length > 0, '迁移有材料选项时应允许上传')
  assert.ok(floatingOptions.length > 0, '流动人口有材料选项时应允许上传')
  assert.equal(emptyOptions.length, 0, '无材料服务时选项为空')
})

test('可选 submit 分发：有 submit 用专业接口，无则回退通用', async () => {
  let genericCalled = false
  let specialCalled = false

  async function pageSubmit(handler, ctx) {
    if (handler?.submit) {
      return handler.submit(ctx)
    }
    genericCalled = true
    return 'generic'
  }

  const migration = createMigrationHandler({})
  await pageSubmit(migration, { applicationId: 1 })
  assert.equal(genericCalled, true)

  const withSubmit = {
    ...createMigrationHandler({}),
    async submit() {
      specialCalled = true
      return 'special'
    }
  }
  genericCalled = false
  const result = await pageSubmit(withSubmit, { applicationId: 2 })
  assert.equal(specialCalled, true)
  assert.equal(result, 'special')
  assert.equal(genericCalled, false)
})

test('旧三类 Handler 提供 getSubmitPermissions 且默认仅 application:submit', () => {
  for (const handler of legacyHandlers) {
    assert.equal(typeof handler.getSubmitPermissions, 'function', `${handler.family} 必须实现 getSubmitPermissions`)
    assert.deepEqual(handler.getSubmitPermissions(), ['application:submit'])
  }
})

/**
 * 页面层提交权限校验语义：Handler 声明的全部权限均需满足
 */
function canSubmitWithPermissions(handler, ownedPermissions, businessType) {
  const required =
    (typeof handler?.getSubmitPermissions === 'function'
      ? handler.getSubmitPermissions(businessType)
      : null) || ['application:submit']
  return required.every((p) => ownedPermissions.includes(p))
}

test('提交按钮权限：仅有 application:submit 时旧业务可提交', () => {
  const migration = createMigrationHandler({})
  assert.equal(
    canSubmitWithPermissions(migration, ['application:submit'], 'MIGRATION_IN'),
    true
  )
  assert.equal(
    canSubmitWithPermissions(migration, [], 'MIGRATION_IN'),
    false
  )
})
