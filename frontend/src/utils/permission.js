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

  if (['SUPER_ADMIN', 'ROLE_SUPER_ADMIN', 'ADMIN', '系统管理员', '超级管理员'].includes(name)) {
    return ROLE_CODE.SUPER_ADMIN
  }
  if (['HOUSEHOLD_ADMIN', 'ROLE_HOUSEHOLD_ADMIN', '户口管理员', '户籍管理员'].includes(name)) {
    return ROLE_CODE.HOUSEHOLD_ADMIN
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

/**
 * 检查是否拥有权限
 */
export function checkPermission(userPermissions, targetPermission) {
  if (!userPermissions || !Array.isArray(userPermissions)) return false
  if (userPermissions.includes('*')) return true
  return userPermissions.includes(targetPermission)
}

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
