const DATA_SCOPE = { ALL: '全部数据', DEPARTMENT: '本部门数据', REGION: '本区域数据', SELF: '本人数据' }
const ROLE_NAMES = {
  QUERY_VIEWER: '查询统计人员',
  POPULATION_MANAGER: '人口信息管理人员',
  SYSTEM_ADMIN: '系统管理员',
  HOUSEHOLD_MANAGER: '户籍管理人员',
  APPROVER: '审批人员'
}
function displayRole(user) {
  if (ROLE_NAMES[user.roleCode]) return ROLE_NAMES[user.roleCode]
  if (typeof user.roleName === 'string' && /[\u4e00-\u9fff]/.test(user.roleName)) return user.roleName
  return '未配置角色'
}
function normalizeUser(raw) {
  const user = raw || {}
  return Object.assign({}, user, {
    displayName: user.realName || user.username || '未命名用户',
    avatarText: (user.realName || user.username || '用').slice(0, 1),
    roleDisplay: displayRole(user),
    departmentDisplay: user.departmentName || '未配置部门',
    dataScopeDisplay: DATA_SCOPE[user.dataScope] || user.dataScope || '未配置',
    permissions: Array.isArray(user.permissions) ? user.permissions : []
  })
}
module.exports = { DATA_SCOPE, ROLE_NAMES, normalizeUser }
