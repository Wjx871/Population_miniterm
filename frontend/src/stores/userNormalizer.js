import { ROLE_CODE, resolvePermissionLevel } from '../constants/roles.js'
import { normalizeRoleCode, resolvePermissions } from '../utils/permission.js'

export function normalizeStoredUser(parsed) {
  if (!parsed || typeof parsed !== 'object') return {}
  const roleCode = normalizeRoleCode(parsed.roleCode, parsed.roleName)
  
  return {
    accessToken: parsed.accessToken || '',
    tokenType: parsed.tokenType || 'Bearer',
    userId: parsed.userId || null,
    username: parsed.username || '',
    realName: parsed.realName || '',
    roleName: parsed.roleName || '',
    roleCode,
    roleLevel: parsed.roleLevel || '',
    permissionLevel: resolvePermissionLevel(parsed.roleLevel || '', roleCode),
    permissions: resolvePermissions(roleCode, parsed.permissions),
    dataScope: parsed.dataScope || null,
    departmentId: parsed.departmentId || null,
    departmentName: parsed.departmentName || '',
    regionCode: parsed.regionCode || '',
  }
}

export function normalizeLoginUser(loginVO) {
  const user = loginVO.user || loginVO
  const rawRoleLevel = user.roleLevel || ''
  const roleCode = normalizeRoleCode(user.roleCode, user.roleName)
  const hasApiPermissions = Array.isArray(user.permissions)
  
  return {
    accessToken: loginVO.token || loginVO.accessToken || '',
    tokenType: loginVO.tokenType || 'Bearer',
    userId: user.userId || null,
    username: user.username || '',
    realName: user.realName || '',
    roleName: user.roleName || '',
    roleCode,
    roleLevel: rawRoleLevel,
    permissionLevel: resolvePermissionLevel(rawRoleLevel, roleCode),
    permissions: hasApiPermissions ? [...user.permissions] : [],
    dataScope: user.dataScope || null,
    departmentId: user.departmentId || null,
    departmentName: user.departmentName || '',
    regionCode: user.regionCode || '',
  }
}
