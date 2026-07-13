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
  // 后端户籍写操作统一使用 household:edit
  HOUSEHOLD_EDIT: 'household:edit',
  HOUSEHOLD_CREATE: 'household:edit',
  HOUSEHOLD_UPDATE: 'household:edit',
  HOUSEHOLD_MEMBER_MANAGE: 'household:edit',
  HOUSEHOLD_DELETE: 'household:edit',
  
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
  
  /** 注销管理 */
  CANCELLATION_VIEW: 'cancellation:view',
  CANCELLATION_PERSON_CREATE: 'cancellation:person:create',
  CANCELLATION_HOUSEHOLD_CREATE: 'cancellation:household:create',
  CANCELLATION_EXECUTE: 'cancellation:execute',
  CANCELLATION_ARCHIVE_VIEW: 'cancellation:archive:view',

  /** 数据导出 */
  DATA_EXPORT_NORMAL: 'data:export:normal',
  DATA_EXPORT_SENSITIVE_APPLY: 'data:export:sensitive:apply',
  DATA_EXPORT_SENSITIVE_EXECUTE: 'data:export:sensitive:execute',
  DATA_EXPORT_SENSITIVE_DOWNLOAD: 'data:export:sensitive:download',
  DATA_EXPORT_LOG_VIEW: 'data:export:log:view',

  /** 重点人口（正式权限码，替换过时的 key:*） */
  KEY_POPULATION_VIEW: 'key-population:view',
  KEY_POPULATION_APPLY: 'key-population:apply',
  KEY_POPULATION_EXECUTE: 'key-population:execute',

  /** 日志查询 */
  LOG_VIEW: 'log:view',

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
