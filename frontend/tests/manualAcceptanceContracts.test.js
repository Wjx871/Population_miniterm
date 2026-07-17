import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const source = (path) => readFileSync(new URL(`../src/${path}`, import.meta.url), 'utf8')

test('审批列表使用 runtime-only 兼容的正式 SFC', () => {
  const list = source('views/approvals/ApprovalList.vue')
  assert.match(list, /ApprovalTable from '\.\/components\/ApprovalTable\.vue'/)
  assert.doesNotMatch(list, /template:\s*`/)
})

test('注销对象单选使用 value 并保持正式枚举', () => {
  const view = source('views/cancellations/CancellationApplicationCreate.vue')
  assert.match(view, /value="PERSON"/)
  assert.match(view, /value="HOUSEHOLD"/)
  assert.doesNotMatch(view, /label="(?:PERSON|HOUSEHOLD)"/)
})

test('家庭户远程搜索使用 ACTIVE、区划和明确错误状态', () => {
  const select = source('components/business/HouseholdSelect.vue')
  const migration = source('views/migrations/MigrationApply.vue')
  const cancellation = source('views/cancellations/CancellationApplicationCreate.vue')
  assert.match(select, /default: 'ACTIVE'/)
  assert.match(select, /query\.regionCode = props\.regionCode/)
  assert.match(select, /家庭户加载失败，请稍后重试/)
  assert.match(migration, /:region-code="form\.toRegionCode"/)
  assert.match(cancellation, /HouseholdSelect v-model="form\.householdId" status="PENDING_CANCELLATION"/)
})

test('迁移表单长标签不换行且迁出资格前置校验', () => {
  const view = source('views/migrations/MigrationApply.vue')
  assert.match(view, /label-width="136px"/)
  assert.match(view, /white-space:nowrap/)
  assert.match(view, /该人员没有当前有效户籍，无法办理迁出/)
})

test('迁出资格提示不隐藏迁出必填项', () => {
  const view = source('views/migrations/MigrationApply.vue')
  assert.match(view, /<template v-if="!isIn">[\s\S]*?prop="toRegionCode"[\s\S]*?prop="toAddress"[\s\S]*?prop="outDate"/)
  assert.doesNotMatch(view, /<el-alert v-if="!isIn && selectedProfile"[\s\S]*?<\/el-alert>\s*<template v-else>/)
})

test('人口详情复用综合查询显示真实关联信息', () => {
  const drawer = source('views/persons/components/PersonDetailDrawer.vue')
  assert.match(drawer, /getComprehensivePersonProfile/)
  assert.match(drawer, /暂无有效户籍或家庭关系/)
  assert.doesNotMatch(drawer, /本阶段|后接入/)
})

test('家庭户状态统一映射为中文', () => {
  const tag = source('components/common/StatusTag.vue')
  assert.match(tag, /PENDING_CANCELLATION: '待注销'/)
  assert.match(tag, /ARCHIVED: '已归档'/)
})
