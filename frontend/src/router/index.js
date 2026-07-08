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
    path: '/home',
    name: 'Home',
    component: Home,
    meta: { title: '首页 - 人口数据库管理系统', requiresAuth: true }
  },
  {
    path: '/',
    redirect: '/home'
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

// 全局前置守卫
router.beforeEach((to, from, next) => {
  // 设置页面标题
  if (to.meta && to.meta.title) {
    document.title = to.meta.title;
  }

  const token = localStorage.getItem('token');
  const requiresAuth = to.matched.some(record => record.meta.requiresAuth);

  if (requiresAuth && !token) {
    // 页面需要登录但未登录，强制重定向到登录页
    next('/login');
  } else if (to.path === '/login' && token) {
    // 已登录状态下访问登录页，重定向到首页
    next('/home');
  } else {
    // 正常放行
    next();
  }
});

export default router;
