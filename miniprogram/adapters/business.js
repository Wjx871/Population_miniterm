const { can } = require('../utils/permission')

const BUSINESS_ENTRIES = Object.freeze([
  Object.freeze({
    key: 'population',
    title: '人口信息',
    description: '查询人口基础档案与详情',
    permission: 'population:view',
    url: '/pages/persons/list/index',
    icon: 'population'
  }),
  Object.freeze({
    key: 'household',
    title: '家庭户',
    description: '查询家庭户档案及成员',
    permission: 'household:view',
    url: '/pages/households/list/index',
    icon: 'household'
  }),
  Object.freeze({
    key: 'permit', title: '居住证查询', description: '查询证件状态与有效期',
    permission: 'residence-permit:view', url: '/pages/permits/list/index', icon: 'residence-permit'
  }),
  Object.freeze({
    key: 'floating', title: '流动人口查询', description: '查询居住登记与有效期',
    permission: 'floating:view', url: '/pages/floating/list/index', icon: 'population'
  }),
  Object.freeze({
    key: 'migration', title: '迁移记录', description: '查询迁入迁出办理结果',
    permission: 'migration:view', url: '/pages/migrations/list/index', icon: 'migration-in'
  })
])

function businessEntries(user) {
  return BUSINESS_ENTRIES.filter((entry) => can(user, entry.permission))
}

module.exports = { BUSINESS_ENTRIES, businessEntries }
