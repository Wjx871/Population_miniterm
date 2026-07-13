import { createRouter, createWebHistory } from 'vue-router';
import { routes } from './routes.js';
import { PERMISSIONS } from '../constants/permissions';
import { resolveLandingPath } from '../utils/routeAccess';

const router = createRouter({
  history: createWebHistory(),
  routes
});

import { useUserStore } from '../stores/user';

function resolveAuthedLanding(userStore) {
  return resolveLandingPath(
    userStore.permissions,
    userStore.permissionLevel,
    router.getRoutes()
  );
}

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

  // 3. 已登录访问 /login 时跳转到首个可访问页面（避免无 statistics:view 时卡在 /home）
  if (to.path === '/login' && userStore.isLoggedIn) {
    return next(resolveAuthedLanding(userStore));
  }

  // 4. 对需要授权的页面进行角色与权限校验
  if (requiresAuth && to.path !== '/403' && to.path !== '/404') {
    if (!userStore.canAccess(to.meta)) {
      // 默认落地页无权限时，直接转到可访问页，避免 /home <-> /403 循环
      if (to.path === '/home') {
        const landing = resolveAuthedLanding(userStore);
        if (landing !== '/home') {
          return next({ path: landing, replace: true });
        }
      }
      return next({ path: '/403', query: { from: to.fullPath }, replace: true });
    }
  }

  // 5. 正常放行
  next();
});

export default router;
