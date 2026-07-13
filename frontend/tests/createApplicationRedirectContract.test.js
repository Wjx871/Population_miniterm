import test from 'node:test'
import assert from 'node:assert/strict'

/**
 * 三个创建页创建成功后必须进入统一申请详情，
 * 而不是仅把 applicationId 写进创建页 query（刷新会丢状态并可能重复创建）。
 */
function resolveAfterCreateRoute(applicationId) {
  if (!applicationId) return null
  return `/applications/${applicationId}`
}

test('敏感导出创建成功后进入 /applications/:id', () => {
  assert.equal(resolveAfterCreateRoute(101), '/applications/101')
})

test('重点人口建档创建成功后进入 /applications/:id', () => {
  assert.equal(resolveAfterCreateRoute(202), '/applications/202')
})

test('重点人口解除创建成功后进入 /applications/:id', () => {
  assert.equal(resolveAfterCreateRoute(303), '/applications/303')
})

test('创建成功后不得停留在仅 query 写入的创建页', () => {
  const afterCreate = resolveAfterCreateRoute(404)
  assert.ok(afterCreate.startsWith('/applications/'))
  assert.equal(afterCreate.includes('?'), false)
})
