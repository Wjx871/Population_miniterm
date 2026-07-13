import { ROLE_CODE } from './roles.js'

export const PERMISSIONS = Object.freeze({
  DASHBOARD_VIEW: 'dashboard:view',
  /** 人口核心模块 */
  POPULATION_VIEW: 'population:view',
  POPULATION_EDIT: 'population:edit',
  SENSITIVE_DATA_VIEW_FULL: 'sensitive-data:view-full',
  
  /** 工作台与数据统计大屏 */
  STATISTICS_VIEW: 'statistics:view',
  
  HOUSEHOLD_VIEW: 'household:view',
  HOUSEHOLD_CREATE: 'household:create',
  HOUSEHOLD_UPDATE: 'household:update',
  HOUSEHOLD_MEMBER_MANAGE: 'household:member:manage',
  HOUSEHOLD_DELETE: 'household:delete',
  
  MIGRATION_VIEW: 'migration:view',
  MIGRATION_IN_CREATE: 'migration:in:create',
  MIGRATION_OUT_CREATE: 'migration:out:create',
  MIGRATION_EXECUTE: 'migration:execute',
  MIGRATION_ARCHIVE_VIEW: 'migration:archive:view',
  
  APPLICATION_VIEW: 'application:view',
  APPLICATION_CREATE: 'application:create',
  APPLICATION_EDIT: 'application:edit',
  APPLICATION_SUBMIT: 'application:submit',
  APPLICATION_WITHDRAW: 'application:withdraw',
  
  MATERIAL_VIEW: 'material:view',
  MATERIAL_UPLOAD: 'material:upload',
  MATERIAL_DELETE: 'material:delete',
  MATERIAL_VERIFY: 'material:verify',
  
  APPROVAL_VIEW: 'approval:view',
  APPROVAL_HANDLE: 'approval:handle',
  
  FLOATING_VIEW: 'floating:view',
  FLOATING_CREATE: 'floating:create',
  FLOATING_EDIT: 'floating:edit',
  FLOATING_EXECUTE: 'floating:execute',
  FLOATING_CLOSE: 'floating:close',
  FLOATING_MANAGE: 'floating:manage',
  
  RESIDENCE_PERMIT_APPLY: 'residence-permit:apply',
  RESIDENCE_PERMIT_VIEW: 'residence-permit:view',
  RESIDENCE_PERMIT_ISSUE: 'residence-permit:issue',
  RESIDENCE_PERMIT_ENDORSE: 'residence-permit:endorse',
  RESIDENCE_PERMIT_CANCEL: 'residence-permit:cancel',
  RESIDENCE_PERMIT_LOG_VIEW: 'residence-permit:log:view',
  RESIDENCE_PERMIT_EXPIRY_VIEW: 'residence-permit:expiry:view',
  
  KEY_VIEW: 'key:view',
  KEY_APPLY: 'key:apply',
  KEY_MANAGE: 'key:manage',
  
  CERTIFICATE_VIEW: 'certificate:view',
  CERTIFICATE_EDIT: 'certificate:edit',
  
  SYSTEM_USER_VIEW: 'system:user:view',
  SYSTEM_USER_MANAGE: 'system:user:manage',
  
  DICTIONARY_VIEW: 'dictionary:view',
  DICTIONARY_MANAGE: 'dictionary:manage',

  REGION_VIEW: 'region:view',
  REGION_MANAGE: 'region:manage'
})

export const ROLE_DEFAULT_PERMISSIONS = Object.freeze({
  // 出于安全考虑，目前所有角色的兜底权限皆为空，强依赖后端返回的 permissions
  [ROLE_CODE.QUERY_VIEWER]: [],
  [ROLE_CODE.POPULATION_MANAGER]: [],
  [ROLE_CODE.HOUSEHOLD_MANAGER]: [],
  [ROLE_CODE.APPROVER]: [],
  [ROLE_CODE.SYSTEM_ADMIN]: []
})
