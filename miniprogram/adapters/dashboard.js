const { can } = require('../utils/permission')

const METRICS = Object.freeze([
  { key: 'pending', field: 'pendingApprovals', label: '待我审批', icon: 'pending-approval', tone: 'warning', permission: 'approval:view', url: '/pages/approvals/list/index' },
  { key: 'processing', field: 'processingApplications', label: '我的处理中申请', icon: 'application', tone: 'primary', permission: 'application:view', url: '/pages/applications/list/index' },
  { key: 'population', field: 'registeredPopulation', label: '总人口', icon: 'population-count', tone: 'primary', permission: 'population:view', url: '/pages/persons/list/index' },
  { key: 'households', field: 'householdCount', label: '家庭户数量', icon: 'household-count', tone: 'primary', permission: 'household:view', url: '/pages/households/list/index' },
  { key: 'migrationIn', field: 'migrationInPeriod', label: '近30日迁入', icon: 'migration-in', tone: 'primary', permission: 'migration:view', url: '/pages/migrations/list/index?type=MIGRATION_IN' },
  { key: 'migrationOut', field: 'migrationOutPeriod', label: '近30日迁出', icon: 'migration-out', tone: 'primary', permission: 'migration:view', url: '/pages/migrations/list/index?type=MIGRATION_OUT' }
])

const ENTRY_DEFINITIONS = Object.freeze([
  { key: 'population', title: '人口信息', desc: '查询人口基础档案', permission: 'population:view', url: '/pages/persons/list/index', icon: 'population' },
  { key: 'household', title: '家庭户', desc: '查询家庭户及成员', permission: 'household:view', url: '/pages/households/list/index', icon: 'household' },
  { key: 'permit', title: '居住证查询', desc: '查询证件状态和有效期', permission: 'residence-permit:view', url: '/pages/permits/list/index', icon: 'residence-permit' },
  { key: 'floating', title: '流动人口查询', desc: '查询当前居住登记', permission: 'floating:view', url: '/pages/floating/list/index', icon: 'population' },
  { key: 'application', title: '我的申请', desc: '查看申请和审批轨迹', permission: 'application:view', url: '/pages/applications/list/index', icon: 'application' },
  { key: 'approval', title: '审批办理', desc: '处理待审批业务事项', permission: 'approval:view', url: '/pages/approvals/list/index', icon: 'approval' }
])

function metricValue(source, field) {
  const value = source && Object.prototype.hasOwnProperty.call(source, field) ? source[field] : null
  const available = typeof value === 'number' && Number.isFinite(value)
  return { value: available ? value : null, valueText: available ? String(value) : '暂无', available }
}

function normalizeMetrics(overview, householdCount, processingApplications, user) {
  const source = Object.assign({}, overview || {}, { householdCount, processingApplications })
  return METRICS.filter((definition) => !user || can(user, definition.permission)).map((definition) => Object.assign({}, definition, metricValue(source, definition.field)))
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
