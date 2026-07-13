import test from 'node:test'
import assert from 'node:assert/strict'
import { normalizeDictionaryList } from '../src/adapters/dictionary.js'

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
