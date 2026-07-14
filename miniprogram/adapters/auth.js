const DATA_SCOPE = { ALL: '全部数据', DEPARTMENT: '本部门数据', REGION: '本区域数据', SELF: '本人数据' }
function normalizeUser(raw) {
  const user = raw || {}
  return Object.assign({}, user, {
    displayName: user.realName || user.username || '未命名用户',
    avatarText: (user.realName || user.username || '用').slice(0, 1),
    roleDisplay: user.roleName || user.roleCode || '未配置角色',
    dataScopeDisplay: DATA_SCOPE[user.dataScope] || user.dataScope || '未配置',
    permissions: Array.isArray(user.permissions) ? user.permissions : []
  })
}
module.exports = { DATA_SCOPE, normalizeUser }
