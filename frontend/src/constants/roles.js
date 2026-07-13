export const ROLE_CODE = Object.freeze({
  QUERY_VIEWER: 'QUERY_VIEWER',
  POPULATION_MANAGER: 'POPULATION_MANAGER',
  HOUSEHOLD_MANAGER: 'HOUSEHOLD_MANAGER',
  APPROVER: 'APPROVER',
  SYSTEM_ADMIN: 'SYSTEM_ADMIN',
})

export const ROLE_LABEL = Object.freeze({
  [ROLE_CODE.QUERY_VIEWER]: '查询统计人员',
  [ROLE_CODE.POPULATION_MANAGER]: '人口信息管理人员',
  [ROLE_CODE.HOUSEHOLD_MANAGER]: '户籍管理人员',
  [ROLE_CODE.APPROVER]: '审批人员',
  [ROLE_CODE.SYSTEM_ADMIN]: '系统管理员',
})

export const ROLE_BADGE_TYPE = Object.freeze({
  [ROLE_CODE.QUERY_VIEWER]: 'info',
  [ROLE_CODE.POPULATION_MANAGER]: 'primary',
  [ROLE_CODE.HOUSEHOLD_MANAGER]: 'success',
  [ROLE_CODE.APPROVER]: 'warning',
  [ROLE_CODE.SYSTEM_ADMIN]: 'danger',
})

export const ROLE_LEVEL_BY_CODE = Object.freeze({
  [ROLE_CODE.QUERY_VIEWER]: 1,
  [ROLE_CODE.POPULATION_MANAGER]: 2,
  [ROLE_CODE.HOUSEHOLD_MANAGER]: 2,
  [ROLE_CODE.APPROVER]: 3,
  [ROLE_CODE.SYSTEM_ADMIN]: 3,
})

export function parseRoleLevel(value) {
  const match = /^L([1-3])$/.exec(String(value || '').toUpperCase())
  return match ? Number(match[1]) : null
}

export function resolvePermissionLevel(roleLevel, roleCode) {
  const parsed = parseRoleLevel(roleLevel)
  if (parsed !== null) return parsed

  return ROLE_LEVEL_BY_CODE[roleCode] ?? 1
}
