import { createRouter, createWebHistory } from 'vue-router';
import Login from '../views/Login.vue';
import Home from '../views/Home.vue';

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: Login,
    meta: { title: '登录 - 人口数据库管理系统' }
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
        meta: { title: '工作台 - 人口数据库管理系统' }
      },
      // 先占位，避免报错
      { path: 'persons', name: 'Persons', component: () => import('../views/persons/PersonList.vue'), meta: { title: '人员管理' } },
      { path: 'households', name: 'Households', component: () => import('../views/households/HouseholdList.vue'), meta: { title: '户籍管理' } },
      { path: 'households/:id', name: 'HouseholdDetail', component: () => import('../views/households/HouseholdDetail.vue'), meta: { title: '户籍详情' } },
      { path: 'migrations/in', name: 'MigrationIn', component: () => import('../views/migrations/MigrationList.vue'), meta: { title: '迁入管理', type: 'in' } },
      { path: 'migrations/out', name: 'MigrationOut', component: () => import('../views/migrations/MigrationList.vue'), meta: { title: '迁出管理', type: 'out' } },
      { path: 'floating-population', name: 'FloatingPopulation', component: { template: '<div>流动人口管理正在建设中...</div>' }, meta: { title: '流动人口' } },
      { path: 'key-population', name: 'KeyPopulation', component: { template: '<div>重点人口管理正在建设中...</div>' }, meta: { title: '重点人口' } },
      { path: 'certificates', name: 'Certificates', component: () => import('../views/certificates/CertificateList.vue'), meta: { title: '证件管理' } },
      { path: 'users', name: 'Users', component: () => import('../views/users/UserList.vue'), meta: { title: '用户管理' } },
      { path: 'dictionary', name: 'Dictionary', component: { template: '<div>数据字典正在建设中...</div>' }, meta: { title: '数据字典' } }
    ]
  },
  // 捕获未定义路由，重定向到首页
  {
    path: '/:pathMatch(.*)*',
    redirect: '/home'
  }
];

const router = createRouter({
  history: createWebHistory(),
  routes
});

import { useUserStore } from '../stores/user';

// 全局前置守卫
router.beforeEach((to, from, next) => {
  // 设置页面标题
  if (to.meta && to.meta.title) {
    document.title = to.meta.title;
  }

  const userStore = useUserStore();
  const requiresAuth = to.matched.some(record => record.meta.requiresAuth);

  if (requiresAuth && !userStore.isLoggedIn) {
    // 页面需要登录但未登录，强制重定向到登录页
    next('/login');
  } else if (to.path === '/login' && userStore.isLoggedIn) {
    // 已登录状态下访问登录页，重定向到首页
    next('/home');
  } else {
    // 正常放行
    next();
  }
});

export default router;
