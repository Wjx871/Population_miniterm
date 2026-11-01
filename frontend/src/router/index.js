import { createRouter, createWebHistory } from 'vue-router'
import { routes } from './routes.js'
import { resolveLandingPath } from '../utils/routeAccess.js'
import { useUserStore } from '../stores/user.js'

const router = createRouter({ history: createWebHistory(), routes })

function resolveAuthedLanding(userStore) {
  return resolveLandingPath(userStore.permissions, userStore.permissionLevel, router.getRoutes())
}

// === 全局路由守卫：认证 + 权限（数据大屏 /statistics/dashboard 同样走这里）===
// 答辩顺序：有 Token 则 restoreSession → 未登录跳登录 → canAccess(meta.permission) → 无权进 403
router.beforeEach(async (to, from, next) => {
  const baseTitle = '人口数据库管理系统'
  document.title = to.meta.title ? `${to.meta.title} - ${baseTitle}` : baseTitle

  const userStore = useUserStore()
  const requiresAuth = to.matched.some((record) => record.meta.requiresAuth)

  // 已有 Token 但本标签页尚未校验会话：先向后端拉当前用户，避免权限缓存过期
  if (requiresAuth && userStore.accessToken && !userStore.sessionChecked) {
    try {
      await userStore.restoreSession()
    } catch {
      return next({ path: '/login', query: { redirect: to.fullPath } })
    }
  }
  // 需要登录但未登录：跳转登录并带回 redirect，登录后可回到大屏等目标页
  if (requiresAuth && !userStore.isLoggedIn) {
    return next({ path: '/login', query: { redirect: to.fullPath } })
  }
  if (to.path === '/login' && userStore.isLoggedIn) return next(resolveAuthedLanding(userStore))
  // 按路由 meta.minLevel / meta.permission 判断；大屏需要 STATISTICS_VIEW
  if (requiresAuth && to.path !== '/403' && to.path !== '/404' && !userStore.canAccess(to.meta)) {
    // 工作台无权时改落到有权限的首页，避免 /home ↔ 403 循环
    if (to.path === '/home') {
      const landing = resolveAuthedLanding(userStore)
      if (landing !== '/home') return next({ path: landing, replace: true })
    }
    return next({ path: '/403', query: { from: to.fullPath }, replace: true })
  }
  next()
})

// 懒加载模块失败时保留当前可用页面，避免路由切换后出现无内容的白屏。
router.onError((error, to, from) => {
  console.error('[路由页面加载失败]', { error, to: to?.fullPath, from: from?.fullPath })
  const fallback = from?.matched?.length ? from.fullPath : '/home'
  if (fallback !== to?.fullPath && router.currentRoute.value.fullPath !== fallback) {
    router.replace(fallback).catch(() => {})
  }
})

export default router
