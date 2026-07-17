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

  await t.test('markCreated(null) 不应将 applicationId 置为 null 后触发 isEdit', () => {
    // 回归：新建 POST 失败（如 409）时后端返回非 2xx，axios 拦截器 reject，
    // saveApplication() 进入 catch 分支，不调用 markCreated。
    // 若调用者意外传入 null，applicationId 也不应被写入，isEdit 必须保持 false。
    const state = useProfessionalDraftState()
    // 模拟意外传 null（调用方没做守卫的情况）
    state.markCreated(null)
    // applicationId 被写为 null → isEdit = Boolean(null) = false
    assert.equal(state.isEdit.value, false, 'null applicationId 不应使 isEdit=true')
    // applicationStatus 被置为 DRAFT，但因为没有 applicationId，hasValidVersion 应允许（新建态）
    assert.equal(state.hasValidVersion.value, true, '无 applicationId 时视为新建，不要求 version')
  })

  await t.test('markCreated 传入合法 ID 后 applyDetailMeta 能正确设置 version', () => {
    // 回归：创建成功 → markCreated(id) → loadApplicationForEdit() → applyDetailMeta(detail)
    // applyDetailMeta 必须能覆盖 markCreated 留下的 null version
    const state = useProfessionalDraftState()
    state.markCreated(456)
    assert.equal(state.professionalVersion.value, null, 'markCreated 后 version 应为 null 等待重载')
    state.applyDetailMeta({
      application: { applicationId: 456, status: 'DRAFT' },
      professional: { version: 3 }
    })
    assert.equal(state.professionalVersion.value, 3, 'applyDetailMeta 应覆写 version 为 3')
    assert.equal(state.hasValidVersion.value, true, '合法 version 后 hasValidVersion 应为 true')
  })
})
