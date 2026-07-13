import { ROLE_CODE, resolvePermissionLevel } from '../constants/roles.js'
import { normalizeRoleCode, resolvePermissions } from '../utils/permission.js'

export function normalizeStoredSession(value) {
  if (!value || typeof value !== 'object') return {}
  return {
    accessToken: typeof value.accessToken === 'string' ? value.accessToken : '',
    tokenType: typeof value.tokenType === 'string' && value.tokenType ? value.tokenType : 'Bearer',
  }
}

export function normalizeStoredUser(value) {
  if (!value || typeof value !== 'object') return {}
  const roleCode = normalizeRoleCode(value.roleCode, value.roleName)
  return {
    ...normalizeStoredSession(value),
    ...normalizeUserInfo({ ...value, roleCode, permissions: resolvePermissions(roleCode, value.permissions) }),
  }
}

export function normalizeUserInfo(user = {}) {
  const roleCode = normalizeRoleCode(user.roleCode, user.roleName)
  return {
    userId: user.userId ?? null,
    username: user.username || '',
    realName: user.realName || '',
    roleName: user.roleName || '',
    roleCode: roleCode || ROLE_CODE.QUERY_VIEWER,
    roleLevel: user.roleLevel || '',
    permissionLevel: resolvePermissionLevel(user.roleLevel || '', roleCode),
    permissions: Array.isArray(user.permissions) ? [...user.permissions] : [],
    dataScope: user.dataScope || null,
    departmentId: user.departmentId ?? null,
    departmentName: user.departmentName || '',
    regionCode: user.regionCode || '',
  }
}

export function normalizeLoginInfo(loginVO = {}) {
  return {
    accessToken: loginVO.token || loginVO.accessToken || '',
    tokenType: loginVO.tokenType || 'Bearer',
    ...normalizeUserInfo(loginVO.user || loginVO),
  }
}

export const normalizeLoginUser = normalizeLoginInfo
