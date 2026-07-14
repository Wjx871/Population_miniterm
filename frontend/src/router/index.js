import { createRouter, createWebHistory } from 'vue-router'
import { routes } from './routes.js'
import { resolveLandingPath } from '../utils/routeAccess.js'
import { useUserStore } from '../stores/user.js'

const router = createRouter({ history: createWebHistory(), routes })

function resolveAuthedLanding(userStore) {
  return resolveLandingPath(userStore.permissions, userStore.permissionLevel, router.getRoutes())
}

router.beforeEach(async (to, from, next) => {
  const baseTitle = '人口数据库管理系统'
  document.title = to.meta.title ? `${to.meta.title} - ${baseTitle}` : baseTitle

  const userStore = useUserStore()
  const requiresAuth = to.matched.some((record) => record.meta.requiresAuth)

  if (requiresAuth && userStore.accessToken && !userStore.sessionChecked) {
    try {
      await userStore.restoreSession()
    } catch {
      return next({ path: '/login', query: { redirect: to.fullPath } })
    }
  }
  if (requiresAuth && !userStore.isLoggedIn) {
    return next({ path: '/login', query: { redirect: to.fullPath } })
  }
  if (to.path === '/login' && userStore.isLoggedIn) return next(resolveAuthedLanding(userStore))
  if (requiresAuth && to.path !== '/403' && to.path !== '/404' && !userStore.canAccess(to.meta)) {
    if (to.path === '/home') {
      const landing = resolveAuthedLanding(userStore)
      if (landing !== '/home') return next({ path: landing, replace: true })
    }
    return next({ path: '/403', query: { from: to.fullPath }, replace: true })
  }
  next()
})

export default router
