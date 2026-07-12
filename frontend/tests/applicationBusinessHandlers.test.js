import test from 'node:test'
import assert from 'node:assert/strict'

import { createMigrationHandler } from '../src/features/applications/handlers/migrationHandler.js'
import { createFloatingHandler } from '../src/features/applications/handlers/floatingHandler.js'
import { createResidencePermitHandler } from '../src/features/applications/handlers/residencePermitHandler.js'

test('Migration Handler 真实契约', async (t) => {
  let executedDirection, executedMigrationType, executedMaterials
  const migrationHandler = createMigrationHandler({
    hasCompleteMigrationMaterials: (dir, type, mats) => {
      executedDirection = dir
      executedMigrationType = type
      executedMaterials = mats
      return true
    }
  })

  await t.test('migrationIn 能够读取 version 和 businessStatus', () => {
    const detail = { migrationIn: { version: 5, businessStatus: 'COMPLETED' } }
    const meta = migrationHandler.getExecutionMeta({ businessType: 'MIGRATION_IN', detail })
    assert.equal(meta.version, 5)
    assert.equal(migrationHandler.isCompleted({ application: { status: 'COMPLETED' }, detail }), true)
  })

  await t.test('migrationOut 能够读取 version 和 businessStatus', () => {
    const detail = { migrationOut: { version: 3, businessStatus: 'COMPLETED' } }
    const meta = migrationHandler.getExecutionMeta({ businessType: 'MIGRATION_OUT', detail })
    assert.equal(meta.version, 3)
    assert.equal(migrationHandler.isCompleted({ application: { status: 'COMPLETED' }, detail }), true)
  })

  await t.test('迁入编辑路由是 /migrations/in/apply', () => {
    const route = migrationHandler.buildEditRoute({ applicationId: 1, detail: { application: { businessType: 'MIGRATION_IN' } } })
    assert.equal(route.path, '/migrations/in/apply')
    assert.equal(route.query.applicationId, 1)
  })

  await t.test('迁出编辑路由是 /migrations/out/apply', () => {
    const route = migrationHandler.buildEditRoute({ applicationId: 1, detail: { application: { businessType: 'MIGRATION_OUT' } } })
    assert.equal(route.path, '/migrations/out/apply')
    assert.equal(route.query.applicationId, 1)
  })

  await t.test('材料函数收到 direction 和 migrationType', () => {
    migrationHandler.hasVerifiedMaterials({
      businessType: 'MIGRATION_IN',
      detail: { migrationIn: { migrationType: 'TYPE_A' } },
      materials: ['mat1']
    })
    assert.equal(executedDirection, 'in')
    assert.equal(executedMigrationType, 'TYPE_A')
    assert.deepEqual(executedMaterials, ['mat1'])
  })

  await t.test('服务缺失时材料核验 fail-closed', () => {
    const emptyHandler = createMigrationHandler({})
    assert.equal(emptyHandler.hasVerifiedMaterials({}), false)
  })
})

test('Residence Permit Handler 真实契约', async (t) => {
  let executedMaterials, executedApplyType, executedReasonCode
  let executedApiArgs = {}

  const permitHandler = createResidencePermitHandler({
    hasVerifiedPermitMaterials: (mats, applyType, reasonCode) => {
      executedMaterials = mats
      executedApplyType = applyType
      executedReasonCode = reasonCode
      return true
    },
    issueResidencePermit: (appId, payload) => {
      executedApiArgs = { api: 'issue', appId, payload }
      return true
    },
    endorseResidencePermit: (appId, version) => {
      executedApiArgs = { api: 'endorse', appId, version }
      return true
    },
    cancelResidencePermitApplication: (appId, version) => {
      executedApiArgs = { api: 'cancel', appId, version }
      return true
    }
  })

  await t.test('首次申领真实路由', () => {
    const route = permitHandler.buildEditRoute({
      applicationId: 2,
      detail: { application: { businessType: 'RESIDENCE_PERMIT_FIRST_ISSUE' } }
    })
    assert.equal(route.path, '/residence-permits/first-issue')
    assert.equal(route.query.applicationId, 2)
  })

  await t.test('签注路由保留 permitId/applicationId/applyType', () => {
    const route = permitHandler.buildEditRoute({
      applicationId: 2,
      detail: { 
        application: { businessType: 'RESIDENCE_PERMIT_ENDORSEMENT' },
        professional: { permitId: 99 }
      }
    })
    assert.equal(route.path, '/residence-permits/99/endorsement/apply')
    assert.equal(route.query.applicationId, 2)
    assert.equal(route.query.permitId, 99)
    assert.equal(route.query.applyType, 'ENDORSEMENT')
  })

  await t.test('注销路由保留 permitId/applicationId/applyType', () => {
    const route = permitHandler.buildEditRoute({
      applicationId: 2,
      detail: { 
        application: { businessType: 'RESIDENCE_PERMIT_CANCELLATION' },
        professional: { permitId: 88 }
      }
    })
    assert.equal(route.path, '/residence-permits/88/cancellation/apply')
    assert.equal(route.query.applicationId, 2)
    assert.equal(route.query.permitId, 88)
    assert.equal(route.query.applyType, 'CANCELLATION')
  })

  await t.test('居住证材料函数收到 materials, applyType, reasonCode', () => {
    permitHandler.hasVerifiedMaterials({
      businessType: 'RESIDENCE_PERMIT_FIRST_ISSUE',
      detail: { subject: { residenceReasonCode: 'WORK' } },
      materials: ['mat1']
    })
    assert.deepEqual(executedMaterials, ['mat1'])
    assert.equal(executedApplyType, 'FIRST_ISSUE')
    assert.equal(executedReasonCode, 'WORK')
  })

  await t.test('首次签发发送 {issuingAuthority, version}', async () => {
    await permitHandler.execute({
      businessType: 'RESIDENCE_PERMIT_FIRST_ISSUE',
      applicationId: 1,
      payload: { issuingAuthority: 'AuthA', version: 2 }
    })
    assert.equal(executedApiArgs.api, 'issue')
    assert.equal(executedApiArgs.appId, 1)
    assert.deepEqual(executedApiArgs.payload, { issuingAuthority: 'AuthA', version: 2 })
  })

  await t.test('签注发送整数 version', async () => {
    await permitHandler.execute({
      businessType: 'RESIDENCE_PERMIT_ENDORSEMENT',
      applicationId: 1,
      payload: { version: 3 }
    })
    assert.equal(executedApiArgs.api, 'endorse')
    assert.equal(executedApiArgs.appId, 1)
    assert.equal(executedApiArgs.version, 3)
  })

  await t.test('注销发送整数 version', async () => {
    await permitHandler.execute({
      businessType: 'RESIDENCE_PERMIT_CANCELLATION',
      applicationId: 1,
      payload: { version: 4 }
    })
    assert.equal(executedApiArgs.api, 'cancel')
    assert.equal(executedApiArgs.appId, 1)
    assert.equal(executedApiArgs.version, 4)
  })

  await t.test('服务缺失时材料核验 fail-closed', () => {
    const emptyHandler = createResidencePermitHandler({})
    assert.equal(emptyHandler.hasVerifiedMaterials({}), false)
  })
})

test('Floating Handler 真实契约', async (t) => {
  let executedMaterials, executedReasonCode

  const floatingHandler = createFloatingHandler({
    hasVerifiedFloatingMaterials: (mats, reasonCode) => {
      executedMaterials = mats
      executedReasonCode = reasonCode
      return true
    }
  })

  await t.test('流动材料函数收到 materials, reasonCode', () => {
    floatingHandler.hasVerifiedMaterials({
      detail: { professional: { residenceReasonCode: 'STUDY' } },
      materials: ['mat2']
    })
    assert.deepEqual(executedMaterials, ['mat2'])
    assert.equal(executedReasonCode, 'STUDY')
  })

  await t.test('服务缺失时材料核验 fail-closed', () => {
    const emptyHandler = createFloatingHandler({})
    assert.equal(emptyHandler.hasVerifiedMaterials({}), false)
  })
})
