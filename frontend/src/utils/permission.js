import { ROLE_CODE, ROLE_LEVEL } from '../constants/roles'
import { ROLE_DEFAULT_PERMISSIONS } from '../constants/permissions'

/**
 * 归一化角色代码，处理后端别名
 */
export function normalizeRoleCode(roleCode, roleName) {
  if (roleCode && ROLE_CODE[roleCode]) {
    return roleCode
  }

  const name = String(roleName || roleCode || '').toUpperCase()

  // 超级管理员别名
  if (['SUPER_ADMIN', 'ROLE_SUPER_ADMIN', 'ADMIN', 'SYSTEM_ADMIN', '系统管理员', '超级管理员'].includes(name)) {
    return ROLE_CODE.SUPER_ADMIN
  }
  // 户口管理员别名（后端人口管理员和户籍管理员均映射为户口管理员）
  if (['HOUSEHOLD_ADMIN', 'ROLE_HOUSEHOLD_ADMIN', 'POPULATION_MANAGER', 'HOUSEHOLD_MANAGER', '户口管理员', '户籍管理员', '人口管理员'].includes(name)) {
    return ROLE_CODE.HOUSEHOLD_ADMIN
  }
  // APPROVER 不映射为超级管理员，仅依赖后端显式审批权限
  if (['QUERY_VIEWER', 'APPROVER', '普通用户', '查询用户', '审批员'].includes(name)) {
    return ROLE_CODE.NORMAL_USER
  }

  return ROLE_CODE.NORMAL_USER
}

/**
 * 解析用户权限列表
 */
export function resolvePermissions(normalizedRoleCode, apiPermissions) {
  if (Array.isArray(apiPermissions)) {
    return [...apiPermissions]
  }
  return ROLE_DEFAULT_PERMISSIONS[normalizedRoleCode] || []
}

/**
 * 获取角色等级
 */
export function getRoleLevel(normalizedRoleCode) {
  return ROLE_LEVEL[normalizedRoleCode] || 1
}

export { checkPermission, canAccessRouteMeta, resolveLandingPath } from './routeAccess'

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
