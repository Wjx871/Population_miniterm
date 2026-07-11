import test from 'node:test'
import assert from 'node:assert/strict'
import {
  isValidVersion,
  canSaveProfessionalDraft,
  mergeSubjectAndProfessional,
  sliceClientPage
} from '../src/utils/professionalApplication.js'

test('isValidVersion', async (t) => {
  await t.test('version 0 被识别为合法', () => {
    assert.equal(isValidVersion(0), true)
  })

  await t.test('null/undefined/NaN 被识别为非法', () => {
    assert.equal(isValidVersion(null), false)
    assert.equal(isValidVersion(undefined), false)
    assert.equal(isValidVersion(NaN), false)
  })
})

test('canSaveProfessionalDraft', async (t) => {
  await t.test('null version 对编辑草稿无效', () => {
    assert.equal(canSaveProfessionalDraft({
      applicationId: '123',
      applicationStatus: 'DRAFT',
      version: null
    }), false)
  })

  await t.test('新建申请不要求 version', () => {
    assert.equal(canSaveProfessionalDraft({
      applicationId: null,
      applicationStatus: 'DRAFT',
      version: null
    }), true)
  })

  await t.test('已有草稿使用合法 version 可保存', () => {
    assert.equal(canSaveProfessionalDraft({
      applicationId: '123',
      applicationStatus: 'DRAFT',
      version: 0
    }), true)
  })
})

test('mergeSubjectAndProfessional', async (t) => {
  await t.test('professional 字段覆盖 subject', () => {
    const subject = { name: '张三', age: 30 }
    const professional = { name: '李四', role: 'admin' }
    const merged = mergeSubjectAndProfessional(subject, professional)
    assert.equal(merged.name, '李四')
    assert.equal(merged.age, 30)
    assert.equal(merged.role, 'admin')
  })

  await t.test('subject 可补齐 registrationNo/currentRegionCode', () => {
    const subject = { registrationNo: 'REG123', currentRegionCode: '310100' }
    const professional = { name: '王五' }
    const merged = mergeSubjectAndProfessional(subject, professional)
    assert.equal(merged.registrationNo, 'REG123')
    assert.equal(merged.currentRegionCode, '310100')
    assert.equal(merged.name, '王五')
  })
})

test('sliceClientPage', async (t) => {
  const records = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15]

  await t.test('客户端分页第一页', () => {
    const page = sliceClientPage(records, 1, 10)
    assert.equal(page.length, 10)
    assert.equal(page[0], 1)
    assert.equal(page[9], 10)
  })

  await t.test('客户端分页末页', () => {
    const page = sliceClientPage(records, 2, 10)
    assert.equal(page.length, 5)
    assert.equal(page[0], 11)
    assert.equal(page[4], 15)
  })

  await t.test('超出范围返回空数组', () => {
    const page = sliceClientPage(records, 3, 10)
    assert.equal(page.length, 0)
  })
})
