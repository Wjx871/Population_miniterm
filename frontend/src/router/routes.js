import { PERMISSIONS } from '../constants/permissions.js';

export const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/Login.vue'),
    meta: { title: '登录' }
  },
  {
    path: '/',
    component: () => import('../layouts/MainLayout.vue'),
    redirect: '/home',
    meta: { requiresAuth: true },
    children: [
      {
        path: 'home',
        name: 'Dashboard',
        component: () => import('../views/dashboard/Dashboard.vue'),
        meta: {
          title: '工作台',
          minLevel: 1,
          permission: PERMISSIONS.STATISTICS_VIEW,
          menu: true,
          group: '工作台',
          order: 1,
          icon: 'HomeFilled'
        }
      },
      {
        path: 'queries/comprehensive',
        name: 'ComprehensiveQuery',
        component: () => import('../views/query/ComprehensiveQuery.vue'),
        meta: { title: '人口综合查询', minLevel: 1, permission: PERMISSIONS.POPULATION_VIEW, menu: true, group: '查询统计', order: 35, icon: 'Search' }
      },
      {
        path: 'queries/households',
        name: 'HouseholdQuery',
        component: () => import('../views/query/HouseholdQuery.vue'),
        meta: { title: '家庭户综合查询', minLevel: 1, permission: PERMISSIONS.HOUSEHOLD_VIEW, menu: true, group: '查询统计', order: 35.1, icon: 'Search' }
      },
      {
        path: 'queries/migration-history',
        name: 'MigrationHistoryQuery',
        component: () => import('../views/query/MigrationHistoryQuery.vue'),
        meta: { title: '迁移历史查询', minLevel: 1, permission: PERMISSIONS.MIGRATION_VIEW, menu: true, group: '查询统计', order: 35.2, icon: 'Search' }
      },
      {
        path: 'logs',
        name: 'LogQuery',
        component: () => import('../views/logs/LogQuery.vue'),
        meta: { title: '系统日志', minLevel: 1, permission: PERMISSIONS.LOG_VIEW, menu: true, group: '查询统计', order: 37, icon: 'Document' }
      },
      {
        path: 'statistics/dashboard',
        name: 'DataDashboard',
        component: () => import('../views/dashboard/DataDashboard.vue'),
        meta: { title: '数据大屏', minLevel: 1, permission: PERMISSIONS.STATISTICS_VIEW, menu: true, group: '查询统计', order: 36, icon: 'DataAnalysis' }
      },
      {
        path: 'exports',
        name: 'ExportRecords',
        component: () => import('../views/exports/ExportRecordList.vue'),
        meta: {
          title: '导出记录',
          minLevel: 1,
          permission: PERMISSIONS.DATA_EXPORT_LOG_VIEW,
          menu: true,
          group: '查询统计',
          order: 37,
          icon: 'Download'
        }
      },
      {
        path: 'exports/normal',
        name: 'NormalExportCreate',
        component: () => import('../views/exports/NormalExportCreate.vue'),
        meta: {
          title: '普通导出',
          minLevel: 1,
          permission: PERMISSIONS.DATA_EXPORT_NORMAL,
          menu: false,
          activeMenu: '/exports',
          group: '查询统计'
        }
      },
      {
        path: 'exports/sensitive',
        name: 'SensitiveExportCreate',
        component: () => import('../views/exports/SensitiveExportCreate.vue'),
        meta: {
          title: '敏感导出申请',
          minLevel: 1,
          permission: PERMISSIONS.DATA_EXPORT_SENSITIVE_APPLY,
          menu: false,
          activeMenu: '/exports',
          group: '查询统计'
        }
      },
      { 
        path: 'persons', 
        name: 'Persons', 
        component: () => import('../views/persons/PersonList.vue'), 
        meta: { 
          title: '人口信息管理',
          minLevel: 1,
          permission: 'population:view',
          menu: true,
          group: '人口户籍',
          order: 10,
          icon: 'User'
        } 
      },
      { 
        path: 'households', 
        name: 'Households', 
        component: () => import('../views/households/HouseholdList.vue'), 
        meta: { 
          title: '户籍管理',
          minLevel: 1,
          permission: 'household:view',
          menu: true,
          group: '人口户籍',
          order: 11,
          icon: 'HomeFilled'
        } 
      },
      { 
        path: 'households/:id', 
        name: 'HouseholdDetail', 
        component: () => import('../views/households/HouseholdDetail.vue'), 
        meta: { 
          title: '户籍详情',
          minLevel: 1,
          permission: 'household:view',
          menu: false,
          activeMenu: '/households'
        } 
      },
      { 
        path: 'migrations/in', 
        name: 'MigrationIn', 
        component: () => import('../views/migrations/MigrationList.vue'), 
        meta: { 
          title: '迁入管理', 
          type: 'in',
          minLevel: 1,
          permission: 'migration:view',
          menu: true,
          group: '业务办理',
          order: 20,
          icon: 'Switch'
        } 
      },
      { 
        path: 'migrations/out', 
        name: 'MigrationOut', 
        component: () => import('../views/migrations/MigrationList.vue'), 
        meta: { 
          title: '迁出管理', 
          type: 'out',
          minLevel: 1,
          permission: 'migration:view',
          menu: true,
          group: '业务办理',
          order: 21,
          icon: 'Switch'
        } 
      },
      {
        path: 'applications',
        name: 'ApplicationList',
        component: () => import('../views/applications/ApplicationList.vue'),
        meta: { title: '我的申请', minLevel: 1, permission: 'application:view', menu: true, group: '业务办理', order: 22, icon: 'Document' }
      },
      {
        path: 'applications/:applicationId',
        name: 'ApplicationDetail',
        component: () => import('../views/applications/ApplicationDetail.vue'),
        meta: { title: '申请详情', minLevel: 1, permission: 'application:view', menu: false, activeMenu: '/applications', group: '业务办理' }
      },
      {
        path: 'migrations/in/apply',
        name: 'MigrationInApply',
        component: () => import('../views/migrations/MigrationApply.vue'),
        meta: { title: '迁入申请', type: 'in', minLevel: 1, permission: 'migration:in:create', menu: false, activeMenu: '/migrations/in', group: '业务办理' }
      },
      {
        path: 'migrations/out/apply',
        name: 'MigrationOutApply',
        component: () => import('../views/migrations/MigrationApply.vue'),
        meta: { title: '迁出申请', type: 'out', minLevel: 1, permission: 'migration:out:create', menu: false, activeMenu: '/migrations/out', group: '业务办理' }
      },
      {
        path: 'approvals',
        name: 'ApprovalList',
        component: () => import('../views/approvals/ApprovalList.vue'),
        meta: { title: '审批中心', minLevel: 1, permission: 'approval:view', menu: true, group: '业务办理', order: 23, icon: 'Finished' }
      },
      {
        path: 'approvals/:approvalId',
        name: 'ApprovalDetail',
        component: () => import('../views/approvals/ApprovalDetail.vue'),
        meta: { title: '审批详情', minLevel: 1, permission: 'approval:view', menu: false, activeMenu: '/approvals', group: '业务办理' }
      },
      {
        path: 'cancellations',
        name: 'Cancellations',
        component: () => import('../views/cancellations/CancellationList.vue'),
        meta: {
          title: '注销管理',
          minLevel: 1,
          permission: PERMISSIONS.CANCELLATION_VIEW,
          menu: true,
          group: '业务办理',
          order: 24,
          icon: 'CircleClose'
        }
      },
      {
        path: 'cancellations/apply',
        name: 'CancellationApply',
        component: () => import('../views/cancellations/CancellationApplicationCreate.vue'),
        meta: {
          title: '注销申请',
          minLevel: 1,
          // 页面内按 person/household 创建权限细判；路由入口用 view 避免误拦
          permission: PERMISSIONS.CANCELLATION_VIEW,
          menu: false,
          activeMenu: '/cancellations',
          group: '业务办理'
        }
      },
      {
        path: 'household-archives',
        name: 'HouseholdArchiveList',
        component: () => import('../views/cancellations/HouseholdArchiveList.vue'),
        meta: {
          title: '家庭户归档',
          minLevel: 1,
          permission: PERMISSIONS.CANCELLATION_ARCHIVE_VIEW,
          menu: false,
          activeMenu: '/cancellations',
          group: '业务办理'
        }
      },
      {
        path: 'residence-archives',
        name: 'ResidenceArchiveList',
        component: () => import('../views/migrations/ResidenceArchiveList.vue'),
        meta: { title: '户籍历史归档', minLevel: 1, permission: 'migration:archive:view', menu: true, group: '人口户籍', order: 12, icon: 'Collection' }
      },
      { 
        path: 'floating-population', 
        name: 'FloatingPopulation', 
        component: () => import('../views/floating/FloatingPopulationList.vue'), 
        meta: { 
          title: '流动人口管理',
          minLevel: 1,
          permission: 'floating:view',
          menu: true,
          group: '扩展业务',
          order: 30,
          icon: 'UserFilled'
        } 
      },
      { 
        path: 'floating-population/apply', 
        name: 'FloatingApplicationCreate', 
        component: () => import('../views/floating/FloatingApplicationCreate.vue'), 
        meta: { 
          title: '流动人口登记申请',
          minLevel: 2,
          permission: 'floating:create',
          menu: false,
          activeMenu: '/floating-population',
          group: '扩展业务'
        } 
      },
      { 
        path: 'floating-population/:floatingId', 
        name: 'FloatingPopulationDetail', 
        component: () => import('../views/floating/FloatingPopulationDetail.vue'), 
        meta: { 
          title: '流动登记详情',
          minLevel: 1,
          permission: 'floating:view',
          menu: false,
          activeMenu: '/floating-population',
          group: '扩展业务'
        } 
      },
      { 
        path: 'residence-permits', 
        name: 'ResidencePermitList', 
        component: () => import('../views/floating/ResidencePermitList.vue'), 
        meta: { 
          title: '居住证管理',
          minLevel: 1,
          permission: 'residence-permit:view',
          menu: true,
          group: '扩展业务',
          order: 31,
          icon: 'Postcard'
        } 
      },
      { 
        path: 'residence-permits/expiring', 
        name: 'ExpiringPermitList', 
        component: () => import('../views/floating/ResidencePermitList.vue'), 
        meta: { 
          title: '居住证到期提醒',
          minLevel: 1,
          permission: 'residence-permit:expiry:view',
          menu: false,
          activeMenu: '/residence-permits',
          group: '扩展业务'
        } 
      },
      { 
        path: 'residence-permits/first-issue', 
        name: 'PermitFirstIssue', 
        component: () => import('../views/floating/PermitApplicationCreate.vue'), 
        meta: { 
          title: '首次申领居住证',
          minLevel: 2,
          permission: 'residence-permit:apply',
          menu: false,
          activeMenu: '/residence-permits',
          group: '扩展业务'
        } 
      },
      { 
        path: 'residence-permits/:permitId/endorsement/apply', 
        name: 'PermitEndorsementApply', 
        component: () => import('../views/floating/PermitApplicationCreate.vue'), 
        meta: { 
          title: '签注申请',
          minLevel: 2,
          permission: 'residence-permit:apply',
          menu: false,
          activeMenu: '/residence-permits',
          group: '扩展业务'
        } 
      },
      { 
        path: 'residence-permits/:permitId/cancellation/apply', 
        name: 'PermitCancellationApply', 
        component: () => import('../views/floating/PermitApplicationCreate.vue'), 
        meta: { 
          title: '注销申请',
          minLevel: 2,
          permission: 'residence-permit:apply',
          menu: false,
          activeMenu: '/residence-permits',
          group: '扩展业务'
        } 
      },
      { 
        path: 'residence-permits/:permitId', 
        name: 'ResidencePermitDetail', 
        component: () => import('../views/floating/ResidencePermitDetail.vue'), 
        meta: { 
          title: '居住证详情',
          minLevel: 1,
          permission: 'residence-permit:view',
          menu: false,
          activeMenu: '/residence-permits',
          group: '扩展业务'
        } 
      },
      {
        path: 'key-population',
        name: 'KeyPopulation',
        component: () => import('../views/key-population/KeyPopulationList.vue'),
        meta: {
          title: '重点人口管理',
          minLevel: 1,
          permission: PERMISSIONS.KEY_POPULATION_VIEW,
          menu: true,
          group: '扩展业务',
          order: 31,
          icon: 'StarFilled'
        }
      },
      {
        path: 'key-population/register',
        name: 'KeyPopulationRegister',
        component: () => import('../views/key-population/KeyPopulationRegisterCreate.vue'),
        meta: {
          title: '重点人口建档',
          minLevel: 1,
          permission: PERMISSIONS.KEY_POPULATION_APPLY,
          menu: false,
          activeMenu: '/key-population',
          group: '扩展业务'
        }
      },
      {
        path: 'key-population/:recordId/history',
        name: 'KeyPopulationHistory',
        component: () => import('../views/key-population/KeyPopulationHistory.vue'),
        meta: {
          title: '重点人口历史',
          minLevel: 1,
          permission: PERMISSIONS.KEY_POPULATION_VIEW,
          menu: false,
          activeMenu: '/key-population',
          group: '扩展业务'
        }
      },
      {
        path: 'key-population/:recordId/release',
        name: 'KeyPopulationRelease',
        component: () => import('../views/key-population/KeyPopulationReleaseCreate.vue'),
        meta: {
          title: '重点人口解除',
          minLevel: 1,
          permission: PERMISSIONS.KEY_POPULATION_APPLY,
          menu: false,
          activeMenu: '/key-population',
          group: '扩展业务'
        }
      },
      {
        path: 'key-population/:recordId',
        name: 'KeyPopulationDetail',
        component: () => import('../views/key-population/KeyPopulationDetail.vue'),
        meta: {
          title: '重点人口详情',
          minLevel: 1,
          permission: PERMISSIONS.KEY_POPULATION_VIEW,
          menu: false,
          activeMenu: '/key-population',
          group: '扩展业务'
        }
      },
      { 
        path: 'certificates', 
        name: 'Certificates', 
        component: () => import('../views/certificates/CertificateList.vue'), 
        meta: { 
          title: '通用证件管理',
          minLevel: 1,
          permission: 'certificate:view',
          menu: true,
          group: '扩展业务',
          order: 32,
          icon: 'Postcard'
        } 
      },
      { 
        path: 'dictionary', 
        name: 'Dictionary', 
        component: () => import('../views/reference/DictionaryManagement.vue'), 
        meta: { 
          title: '数据字典',
          minLevel: 1,
          permission: 'dictionary:view',
          menu: true,
          group: '系统管理',
          order: 41,
          icon: 'Setting'
        } 
      },
      {
        path: 'region',
        name: 'Region',
        component: () => import('../views/reference/RegionManagement.vue'),
        meta: {
          title: '行政区划',
          minLevel: 1,
          permission: 'region:view',
          menu: true,
          group: '系统管理',
          order: 42,
          icon: 'Location'
        }
      },
      {
        path: 'logs/operations',
        name: 'OperationLogs',
        component: () => import('../views/logs/OperationLogList.vue'),
        meta: {
          title: '操作日志',
          minLevel: 1,
          permission: PERMISSIONS.LOG_VIEW,
          menu: true,
          group: '系统管理',
          order: 43,
          icon: 'Document'
        }
      },
      {
        path: 'logs/operations/:id',
        name: 'OperationLogDetail',
        component: () => import('../views/logs/OperationLogDetail.vue'),
        meta: {
          title: '操作日志详情',
          minLevel: 1,
          permission: PERMISSIONS.LOG_VIEW,
          menu: false,
          activeMenu: '/logs/operations',
          group: '系统管理'
        }
      },
      {
        path: 'logs/logins',
        name: 'LoginLogs',
        component: () => import('../views/logs/LoginLogList.vue'),
        meta: {
          title: '登录日志',
          minLevel: 1,
          permission: PERMISSIONS.LOG_VIEW,
          menu: true,
          group: '系统管理',
          order: 44,
          icon: 'User'
        }
      },
      {
        path: '403',
        name: 'Forbidden',
        component: () => import('../views/error/Forbidden.vue'),
        meta: { title: '无权访问', menu: false }
      },
      {
        path: '404',
        name: 'NotFound',
        component: () => import('../views/error/NotFound.vue'),
        meta: { title: '页面不存在', menu: false }
      }
    ]
  },
  // 捕获未定义路由，重定向到404
  {
    path: '/:pathMatch(.*)*',
    redirect: '/404'
  }
];
