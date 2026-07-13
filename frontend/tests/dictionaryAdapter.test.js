import test from 'node:test'
import assert from 'node:assert/strict'
import {
  normalizeDictionaryList,
  toDictionaryUpdatePayload,
  resolveDictionaryOptionValue,
} from '../src/adapters/dictionary.js'

test('dictionaryAdapter: maps exact fields and filters status when includeInactive is false', () => {
  const items = [
    { dictId: 1, dictType: 'T1', dictCode: 'V1', dictName: 'L1', sortNo: 1, status: 'ENABLED', version: 0 },
    { dictId: 2, dictType: 'T1', dictCode: 'V2', dictName: 'L2', sortNo: 2, status: 'DISABLED', version: 0 },
  ]
  
  const normalized = normalizeDictionaryList(items, false)
  
  assert.equal(normalized.length, 1)
  assert.equal(normalized[0].value, 'V1')
  assert.equal(normalized[0].label, 'L1')
  assert.equal(normalized[0].id, 1)
  assert.equal(normalized[0].disabled, false)
})

test('dictionaryAdapter: keeps all items when includeInactive is true', () => {
  const items = [
    { dictId: 1, dictType: 'T1', dictCode: 'V1', dictName: 'L1', sortNo: 1, status: 'ENABLED', version: 0 },
    { dictId: 2, dictType: 'T1', dictCode: 'V2', dictName: 'L2', sortNo: 2, status: 'DISABLED', version: 0 },
  ]
  
  const normalized = normalizeDictionaryList(items, true)
  
  assert.equal(normalized.length, 2)
  assert.equal(normalized[1].disabled, true)
})

test('dictionaryAdapter: toDictionaryUpdatePayload only returns expected fields', () => {
  const form = {
    dictType: 'CERTIFICATE_TYPE',
    dictCode: 'PASSPORT',
    dictName: '护照',
    sortNo: 10,
    status: 'ENABLED',
    version: 2
  }

  const payload = toDictionaryUpdatePayload(form)

  assert.deepEqual(payload, {
    displayName: '护照',
    sortNo: 10,
    version: 2
  })

  assert.equal(payload.dictionaryType, undefined)
  assert.equal(payload.dictionaryCode, undefined)
  assert.equal(payload.status, undefined)
})

test('民族选择使用 label 模式提交中文名称', () => {
  const item = {
    value: 'HAN',
    label: '汉族',
  }

  assert.equal(
    resolveDictionaryOptionValue(item, 'label'),
    '汉族'
  )

  assert.equal(
    resolveDictionaryOptionValue(item, 'code'),
    'HAN'
  )

  assert.equal(
    resolveDictionaryOptionValue(item),
    'HAN'
  )
})
