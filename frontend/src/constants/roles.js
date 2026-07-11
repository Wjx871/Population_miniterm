export const ROLE_CODE = Object.freeze({
  NORMAL_USER: 'NORMAL_USER',
  HOUSEHOLD_ADMIN: 'HOUSEHOLD_ADMIN',
  SUPER_ADMIN: 'SUPER_ADMIN',
})

export const ROLE_LEVEL = Object.freeze({
  [ROLE_CODE.NORMAL_USER]: 1,
  [ROLE_CODE.HOUSEHOLD_ADMIN]: 2,
  [ROLE_CODE.SUPER_ADMIN]: 3,
})

export const ROLE_LABEL = Object.freeze({
  [ROLE_CODE.NORMAL_USER]: '普通用户',
  [ROLE_CODE.HOUSEHOLD_ADMIN]: '户口管理员',
  [ROLE_CODE.SUPER_ADMIN]: '超级管理员',
})

export const ROLE_BADGE_TYPE = Object.freeze({
  [ROLE_CODE.NORMAL_USER]: 'info',
  [ROLE_CODE.HOUSEHOLD_ADMIN]: 'primary',
  [ROLE_CODE.SUPER_ADMIN]: 'danger',
})
