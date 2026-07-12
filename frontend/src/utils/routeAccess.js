/**
 * 路由访问与登录落地路径的纯函数工具（无角色常量依赖，便于 Node 单测）。
 */

export function checkPermission(userPermissions, targetPermission) {
  if (!userPermissions || !Array.isArray(userPermissions)) return false
  if (userPermissions.includes('*')) return true
  return userPermissions.includes(targetPermission)
}

/**
 * 判断路由 meta 是否可访问（与 userStore.canAccess 语义一致）。
 */
export function canAccessRouteMeta(userPermissions, permissionLevel, meta) {
  if (!meta) return true
  if (meta.minLevel && (permissionLevel || 0) < meta.minLevel) return false
  if (meta.permission && !checkPermission(userPermissions, meta.permission)) return false
  return true
}

/**
 * 登录后/无权限返回时的可访问落地路径。
 * 优先候选路径，再按路由表中第一个可访问菜单，最后回退 /403。
 */
export function resolveLandingPath(userPermissions, permissionLevel, routes = [], preferredPaths = ['/home', '/queries/comprehensive']) {
  const permissions = Array.isArray(userPermissions) ? userPermissions : []
  const level = permissionLevel || 0

  for (const path of preferredPaths) {
    const route = routes.find((item) => item.path === path)
    if (route && canAccessRouteMeta(permissions, level, route.meta)) {
      return path
    }
  }

  const menuRoutes = routes
    .filter((route) => route.meta && route.meta.menu === true && canAccessRouteMeta(permissions, level, route.meta))
    .sort((a, b) => (a.meta.order || 99) - (b.meta.order || 99))

  if (menuRoutes.length > 0) {
    const path = menuRoutes[0].path
    return path.startsWith('/') ? path : `/${path}`
  }

  return '/403'
}
