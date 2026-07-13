import test from 'node:test'
import assert from 'node:assert/strict'
import { normalizeDictionaryList } from '../src/adapters/dictionary.js'

test('dictionaryAdapter: maps exact fields and filters status', () => {
  const input = [
    { dictCode: 'ID', dictName: '身份证', status: 'ACTIVE' },
    { dictCode: 'PP', dictName: '护照', status: '正常' },
    { dictCode: 'DL', dictName: '驾照', status: '启用' },
    { dictCode: 'X', dictName: '未知状态', status: 'unknown' },
    { dictCode: 'Y', dictName: '停用', status: 'INACTIVE' },
    { dictCode: 'Z', dictName: '布尔值', status: true },
    { dictCode: 'N', dictName: '数字值', status: 1 }
  ]
  
  const result = normalizeDictionaryList(input)
  
  assert.equal(result.length, 3)
  assert.equal(result[0].value, 'ID')
  assert.equal(result[0].label, '身份证')
  assert.equal(result[1].value, 'PP')
  assert.equal(result[1].label, '护照')
  assert.equal(result[2].value, 'DL')
  assert.equal(result[2].label, '驾照')
})
