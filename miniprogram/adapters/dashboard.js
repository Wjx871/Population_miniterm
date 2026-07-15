const { can } = require('../utils/permission')

const METRICS = Object.freeze([
  { key: 'population', field: 'registeredPopulation', label: '总人口', icon: 'population-count', tone: 'primary' },
  { key: 'households', field: 'householdCount', label: '家庭户数量', icon: 'household-count', tone: 'primary' },
  { key: 'pending', field: 'pendingApprovals', label: '待审批', icon: 'pending-approval', tone: 'warning' },
  { key: 'migrationIn', field: 'migrationInPeriod', label: '近30日迁入', icon: 'migration-in', tone: 'primary' },
  { key: 'migrationOut', field: 'migrationOutPeriod', label: '近30日迁出', icon: 'migration-out', tone: 'primary' },
  { key: 'expiringPermits', field: 'expiringResidencePermits', label: '即将到期居住证', icon: 'residence-permit', tone: 'warning' }
])

const ENTRY_DEFINITIONS = Object.freeze([
  { key: 'population', title: '人口信息', desc: '查询人口基础档案', permission: 'population:view', url: '/pages/persons/list/index', icon: 'population' },
  { key: 'household', title: '家庭户', desc: '查询家庭户及成员', permission: 'household:view', url: '/pages/households/list/index', icon: 'household' },
  { key: 'application', title: '我的申请', desc: '查看申请和审批轨迹', permission: 'application:view', url: '/pages/applications/list/index', icon: 'application' },
  { key: 'approval', title: '审批办理', desc: '处理待审批业务事项', permission: 'approval:view', url: '/pages/approvals/list/index', icon: 'approval' },
  { key: 'profile', title: '个人中心', desc: '账号、权限和服务状态', permission: null, url: '/pages/profile/index', icon: 'profile' }
])

function metricValue(source, field) {
  const value = source && Object.prototype.hasOwnProperty.call(source, field) ? source[field] : null
  const available = typeof value === 'number' && Number.isFinite(value)
  return { value: available ? value : null, valueText: available ? String(value) : '暂无', available }
}

function normalizeMetrics(overview, householdCount) {
  const source = Object.assign({}, overview || {}, { householdCount })
  return METRICS.map((definition) => Object.assign({}, definition, metricValue(source, definition.field)))
}

function normalizeHealth(raw) {
  if (!raw || typeof raw !== 'object') return { available: false, text: '状态暂不可用', type: 'neutral' }
  if (raw.database === 'UP') return { available: true, text: '系统运行正常', type: 'success' }
  if (raw.database === 'DOWN') return { available: true, text: '系统状态异常', type: 'danger' }
  return { available: false, text: '状态暂不可用', type: 'neutral' }
}

function dashboardEntries(user) {
  return ENTRY_DEFINITIONS.filter((entry) => !entry.permission || can(user, entry.permission))
}

module.exports = { METRICS, ENTRY_DEFINITIONS, normalizeMetrics, normalizeHealth, dashboardEntries }
