const { normalizeUser } = require('./auth')

const DUTY_DEFINITIONS = Object.freeze([
  { key: 'overview', label: '查看工作概览与待办', permissions: ['statistics:view'] },
  { key: 'population', label: '查询人口档案', permissions: ['population:view'] },
  { key: 'household', label: '查询家庭户信息', permissions: ['household:view'] },
  { key: 'application', label: '查看本人申请及办理进度', permissions: ['application:view'] },
  { key: 'approval-view', label: '查看权限范围内的审批事项', permissions: ['approval:view'] },
  { key: 'approval-handle', label: '处理权限范围内的审批事项', permissions: ['approval:handle'] },
  { key: 'migration', label: '查看迁移业务信息', permissions: ['migration:view'] },
  { key: 'cancellation', label: '查看注销业务信息', permissions: ['cancellation:view'] },
  { key: 'floating', label: '查看流动人口业务信息', permissions: ['floating:view'] },
  { key: 'permit', label: '查看居住证业务信息', permissions: ['residence-permit:view'] },
  { key: 'key-population', label: '查看重点人口业务信息', permissions: ['key-population:view'] }
])

const FEATURE_DEFINITIONS = Object.freeze([
  { key: 'population', title: '人口信息', description: '查询人口基础档案', permission: 'population:view', icon: 'population', url: '/pages/persons/list/index' },
  { key: 'household', title: '家庭户', description: '查询家庭户及成员', permission: 'household:view', icon: 'household', url: '/pages/households/list/index' },
  { key: 'application', title: '我的申请', description: '查看申请结果与办理进度', permission: 'application:view', icon: 'application', url: '/pages/applications/list/index' },
  { key: 'approval', title: '审批办理', description: '查看并处理审批事项', permission: 'approval:view', icon: 'approval', url: '/pages/approvals/list/index' }
])

function normalizeProfile(raw) {
  const user = normalizeUser(raw)
  const permissions = new Set(user.permissions)
  return Object.assign({}, user, {
    accountStatusDisplay: user.userId ? '正常' : '状态暂不可用',
    duties: DUTY_DEFINITIONS.filter((item) => item.permissions.some((permission) => permissions.has(permission))),
    features: FEATURE_DEFINITIONS.filter((item) => permissions.has(item.permission))
  })
}

module.exports = { DUTY_DEFINITIONS, FEATURE_DEFINITIONS, normalizeProfile }
