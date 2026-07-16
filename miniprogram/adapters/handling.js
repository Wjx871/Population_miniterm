const { can } = require('../utils/permission')

const HANDLING_ENTRIES = Object.freeze([
  Object.freeze({
    key: 'application',
    title: '我的申请',
    description: '查看申请记录与办理进度',
    permission: 'application:view',
    url: '/pages/applications/list/index',
    icon: 'application'
  }),
  Object.freeze({
    key: 'endorsement',
    title: '居住证签注申请',
    description: '移动端轻量提交，审批后由 Web 端办结',
    permissions: ['residence-permit:apply', 'residence-permit:expiry:view'],
    url: '/pages/permits/endorsement/index',
    icon: 'residence-permit'
  }),
  Object.freeze({
    key: 'approval',
    title: '审批办理',
    description: '处理权限范围内的待办事项',
    permission: 'approval:view',
    url: '/pages/approvals/list/index',
    icon: 'approval'
  })
])

function handlingEntries(user) {
  return HANDLING_ENTRIES.filter((entry) => entry.permissions
    ? entry.permissions.every((permission) => can(user, permission))
    : can(user, entry.permission))
}

function normalizePendingSummary(raw) {
  const value = raw && raw.pendingApprovals
  const available = typeof value === 'number' && Number.isFinite(value)
  return {
    available,
    value: available ? value : null,
    valueText: available ? String(value) : ''
  }
}

module.exports = { HANDLING_ENTRIES, handlingEntries, normalizePendingSummary }
