import { ROLE_CODE } from '../constants/roles.js'
import { ROLE_DEFAULT_PERMISSIONS } from '../constants/permissions.js'

/**
 * 归一化角色代码
 */
export function normalizeRoleCode(roleCode, roleName) {
  if (roleCode && ROLE_CODE[roleCode]) {
    return roleCode
  }

  const name = String(roleName || roleCode || '').toUpperCase()

  if (['SYSTEM_ADMIN', '超级管理员', '系统管理员', 'ADMIN'].includes(name)) {
    return ROLE_CODE.SYSTEM_ADMIN
  }
  if (['APPROVER', '审批人员', '审批员'].includes(name)) {
    return ROLE_CODE.APPROVER
  }
  if (['HOUSEHOLD_MANAGER', '户籍管理人员', '户籍管理员', '户口管理员'].includes(name)) {
    return ROLE_CODE.HOUSEHOLD_MANAGER
  }
  if (['POPULATION_MANAGER', '人口信息管理人员', '人口管理员'].includes(name)) {
    return ROLE_CODE.POPULATION_MANAGER
  }
  
  return ROLE_CODE.QUERY_VIEWER
}

/**
 * 解析用户权限列表，遵循硬约束
 */
export function resolvePermissions(normalizedRoleCode, apiPermissions) {
  // 如果明确返回了 permissions 数组，这就是权威依据，哪怕是空数组
  if (Array.isArray(apiPermissions)) {
    return [...apiPermissions]
  }
  
  // 没有返回 permissions 的情况（仅考虑兼容极其老的本地缓存）
  // 为了安全，不再基于 roleCode 去自动展开权限，避免无权限账户扩大权限
  // 若确实需要兜底，可返回空数组或极少权限。
  return ROLE_DEFAULT_PERMISSIONS[normalizedRoleCode] || []
}

export { checkPermission, canAccessRouteMeta, resolveLandingPath } from './routeAccess.js'

/**
 * 检查是否拥有任意一个权限
 */
export function checkAnyPermission(userPermissions, targetPermissions) {
  if (!userPermissions || !Array.isArray(userPermissions)) return false
  if (userPermissions.includes('*')) return true
  if (!Array.isArray(targetPermissions)) {
    return userPermissions.includes(targetPermissions)
  }
  return targetPermissions.some(p => userPermissions.includes(p))
}
