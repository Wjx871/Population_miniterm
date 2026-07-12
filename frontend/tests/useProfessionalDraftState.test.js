import test from 'node:test'
import assert from 'node:assert/strict'
import { useProfessionalDraftState } from '../src/composables/useProfessionalDraftState.js'

test('useProfessionalDraftState 状态流转', async (t) => {
  await t.test('初始时 applicationStatus 为 null，isReadOnly 应该为 false', () => {
    const state = useProfessionalDraftState()
    assert.equal(state.isReadOnly.value, false)
    assert.equal(state.isEdit.value, false)
  })

  await t.test('新建态（调用 markCreated）允许编辑', () => {
    const state = useProfessionalDraftState()
    state.markCreated(123)
    assert.equal(state.isEdit.value, true)
    assert.equal(state.applicationStatus.value, 'DRAFT')
    assert.equal(state.isReadOnly.value, false)
  })

  await t.test('非 DRAFT 的已有申请只读且不可保存', () => {
    const state = useProfessionalDraftState()
    state.applyDetailMeta({
      application: { applicationId: 123, status: 'PENDING' },
      professional: { version: 0 }
    })
    assert.equal(state.isEdit.value, true)
    assert.equal(state.isReadOnly.value, true)
    assert.equal(state.hasValidVersion.value, false)
  })
})
