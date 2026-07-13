import test from 'node:test'
import assert from 'node:assert/strict'

import {
  normalizeLogRecord,
  toOperationLogQueryParams,
  toLoginLogQueryParams
} from '../src/adapters/log.js'

test('操作日志查询参数冻结真实字段', () => {
  const params = toOperationLogQueryParams({
    username: 'admin',
    operationType: 'LOGIN',
    module: 'AUTH',
    result: 'SUCCESS',
    ip: '127.0.0.1',
    dateFrom: '2026-07-01T00:00:00',
    dateTo: '2026-07-13T23:59:59',
    current: 2,
    size: 20,
    keyword: 'no',
    password: 'secret'
  })
  assert.equal(params.keyword, undefined)
  assert.equal(params.password, undefined)
  assert.equal(params.username, 'admin')
  assert.equal(params.operationType, 'LOGIN')
  assert.equal(params.module, 'AUTH')
  assert.equal(params.dateFrom, '2026-07-01T00:00:00')
})

test('登录日志查询参数冻结真实字段', () => {
  const params = toLoginLogQueryParams({
    username: 'u1',
    result: 'LOGIN_SUCCESS',
    ip: '1.1.1.1',
    dateFrom: '2026-07-01T00:00:00',
    dateTo: '2026-07-02T00:00:00',
    current: 1,
    size: 10,
    operationType: 'should-not-send'
  })
  assert.equal(params.operationType, undefined)
  assert.equal(params.result, 'LOGIN_SUCCESS')
  assert.equal(params.ip, '1.1.1.1')
})

test('日志记录规范化不引入敏感字段', () => {
  const log = normalizeLogRecord({
    logId: 3,
    username: 'admin',
    operationType: 'DATA_EXPORT_DOWNLOAD',
    moduleName: 'EXPORT',
    operationResult: 'SUCCESS',
    ipAddress: '10.0.0.1',
    detail: 'ok',
    password: 'x',
    token: 'y'
  })
  assert.equal(log.logId, 3)
  assert.equal(log.password, undefined)
  assert.equal(log.token, undefined)
  assert.equal(log.username, 'admin')
})
