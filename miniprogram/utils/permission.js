function permissionsOf(user) { return Array.isArray(user && user.permissions) ? user.permissions : [] }
function can(user, permission) { return permissionsOf(user).includes(permission) }
function canAny(user, permissions) { return permissions.some((permission) => can(user, permission)) }
function guard(permission) {
  const user = getApp().globalData.user
  if (can(user, permission)) return true
  wx.showToast({ title: '权限不足', icon: 'none' })
  return false
}
module.exports = { permissionsOf, can, canAny, guard }
