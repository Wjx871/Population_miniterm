import test from 'node:test'
import assert from 'node:assert/strict'

import { createMigrationHandler } from '../src/features/applications/handlers/migrationHandler.js'
import { createFloatingHandler } from '../src/features/applications/handlers/floatingHandler.js'
import { createResidencePermitHandler } from '../src/features/applications/handlers/residencePermitHandler.js'

// 简化的 handler 注册表，纯用于测试不依赖外部请求
function getTestHandler(businessType) {
  const handlers = [
    createMigrationHandler({}),
    createFloatingHandler({}),
    createResidencePermitHandler({
      getPermitMaterialOptions: (type, reasonCode) => {
        return { type, reasonCode }
      }
    })
  ]
  for (const handler of handlers) {
    if (handler.supports(businessType)) {
      return handler
    }
  }
  return null
}

test('业务类型映射与未知处理', async (t) => {
  await t.test('六种专业业务均能找到正确 handler', () => {
    assert.equal(getTestHandler('MIGRATION_IN').family, 'migration')
    assert.equal(getTestHandler('MIGRATION_OUT').family, 'migration')
    assert.equal(getTestHandler('FLOATING_REGISTRATION').family, 'floating')
    assert.equal(getTestHandler('RESIDENCE_PERMIT_FIRST_ISSUE').family, 'permit')
    assert.equal(getTestHandler('RESIDENCE_PERMIT_ENDORSEMENT').family, 'permit')
    assert.equal(getTestHandler('RESIDENCE_PERMIT_CANCELLATION').family, 'permit')
  })

  await t.test('未知业务类型返回 null', () => {
    assert.equal(getTestHandler('UNKNOWN_BUSINESS'), null)
  })
})

test('三类居住证业务返回不同执行权限', () => {
  const handler = getTestHandler('RESIDENCE_PERMIT_FIRST_ISSUE')
  const meta1 = handler.getExecutionMeta({ businessType: 'RESIDENCE_PERMIT_FIRST_ISSUE' })
  assert.equal(meta1.permission, 'residence-permit:issue')
  
  const meta2 = handler.getExecutionMeta({ businessType: 'RESIDENCE_PERMIT_ENDORSEMENT' })
  assert.equal(meta2.permission, 'residence-permit:endorse')

  const meta3 = handler.getExecutionMeta({ businessType: 'RESIDENCE_PERMIT_CANCELLATION' })
  assert.equal(meta3.permission, 'residence-permit:cancel')
})

test('首次申领材料读取 subject.residenceReasonCode', () => {
  const handler = getTestHandler('RESIDENCE_PERMIT_FIRST_ISSUE')
  
  const detail = {
    subject: { residenceReasonCode: 'WORK' }
  }
  
  // 这里注入了 stub 的 getPermitMaterialOptions，会返回带 reasonCode 的对象
  const options = handler.getMaterialOptions({ businessType: 'RESIDENCE_PERMIT_FIRST_ISSUE', detail })
  assert.equal(options.reasonCode, 'WORK')
})

