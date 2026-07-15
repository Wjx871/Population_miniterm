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
  })
])

function businessEntries(user) {
  return BUSINESS_ENTRIES.filter((entry) => can(user, entry.permission))
}

module.exports = { BUSINESS_ENTRIES, businessEntries }
