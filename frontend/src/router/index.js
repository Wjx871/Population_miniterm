import { createRouter, createWebHistory } from 'vue-router';
import Login from '../views/Login.vue';

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: Login,
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
          permission: 'population:view',
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
        meta: { title: '人口综合查询', minLevel: 1, permission: 'population:view', menu: true, group: '查询统计', order: 35, icon: 'Search' }
      },
      {
        path: 'statistics/dashboard',
        name: 'DataDashboard',
        component: () => import('../views/dashboard/DataDashboard.vue'),
        meta: { title: '数据大屏', minLevel: 1, permission: 'population:view', menu: true, group: '查询统计', order: 36, icon: 'DataAnalysis' }
      },
      { 
        path: 'persons', 
        name: 'Persons', 
        component: () => import('../views/persons/PersonList.vue'), 
        meta: { 
          title: '人口信息管理',
          minLevel: 1,
          permission: 'person:view',
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
        component: { template: '<div>重点人口管理正在建设中...</div>' }, 
        meta: { 
          title: '重点人口管理',
          minLevel: 2,
          permission: 'key:view',
          menu: false,
          group: '扩展业务',
          order: 31,
          icon: 'StarFilled'
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
        path: 'users', 
        name: 'Users', 
        component: () => import('../views/users/UserList.vue'), 
        meta: { 
          title: '用户管理',
          minLevel: 3,
          permission: 'user:view',
          menu: true,
          group: '系统管理',
          order: 40,
          icon: 'User'
        } 
      },
      { 
        path: 'dictionary', 
        name: 'Dictionary', 
        component: { template: '<div>数据字典正在建设中...</div>' }, 
        meta: { 
          title: '数据字典',
          minLevel: 3,
          permission: 'dictionary:view',
          menu: true,
          group: '系统管理',
          order: 41,
          icon: 'Setting'
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

const router = createRouter({
  history: createWebHistory(),
  routes
});

import { useUserStore } from '../stores/user';

// 全局前置守卫
router.beforeEach((to, from, next) => {
  // 1. 设置页面标题
  const baseTitle = '人口数据库管理系统';
  document.title = to.meta.title ? `${to.meta.title} - ${baseTitle}` : baseTitle;

  const userStore = useUserStore();
  const requiresAuth = to.matched.some(record => record.meta.requiresAuth);

  // 2. 判断是否需要登录
  if (requiresAuth && !userStore.isLoggedIn) {
    return next({ path: '/login', query: { redirect: to.fullPath } });
  }

  // 3. 已登录访问 /login 时跳转 /home
  if (to.path === '/login' && userStore.isLoggedIn) {
    return next('/home');
  }

  // 4. 对需要授权的页面进行角色与权限校验
  if (requiresAuth && to.path !== '/403' && to.path !== '/404') {
    if (!userStore.canAccess(to.meta)) {
      return next({ path: '/403', query: { from: to.fullPath }, replace: true });
    }
  }

  // 5. 正常放行
  next();
});

export default router;
