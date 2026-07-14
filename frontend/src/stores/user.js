import { defineStore } from 'pinia'
import { getCurrentUser, login as loginApi, logout as logoutApi } from '../api/auth.js'
import { ROLE_CODE, ROLE_LABEL } from '../constants/roles.js'
import { checkPermission, checkAnyPermission } from '../utils/permission.js'
import { normalizeLoginInfo, normalizeStoredSession, normalizeUserInfo } from './userNormalizer.js'
import { clearAllReferenceCache } from '../services/referenceDataCache.js'

export const STORAGE_KEY = 'population_user_v2'
let restorePromise = null

function loadStoredSession() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    return raw ? normalizeStoredSession(JSON.parse(raw)) : {}
  } catch {
    return {}
  }
}

export const useUserStore = defineStore('user', {
  state: () => {
    const saved = loadStoredSession()
    return {
      accessToken: saved.accessToken || '', tokenType: saved.tokenType || 'Bearer',
      userId: null, username: '', realName: '', roleName: '', roleCode: ROLE_CODE.QUERY_VIEWER,
      roleLevel: '', permissionLevel: 1, permissions: [], dataScope: null,
      departmentId: null, departmentName: '', regionCode: '', sessionChecked: !saved.accessToken,
    }
  },
  getters: {
    isLoggedIn: (state) => Boolean(state.accessToken && state.sessionChecked),
    displayName: (state) => state.realName || state.username || '用户',
    roleLabel: (state) => ROLE_LABEL[state.roleCode] || state.roleName || '查询统计人员',
    isSuperAdmin: (state) => state.roleCode === ROLE_CODE.SYSTEM_ADMIN,
  },
  actions: {
    setLoginInfo(loginVO) {
      Object.assign(this, normalizeLoginInfo(loginVO), { sessionChecked: true })
      this.persistSession()
    },
    async login(form) {
      const loginVO = await loginApi(form)
      this.setLoginInfo(loginVO)
      return loginVO
    },
    async restoreSession() {
      if (this.sessionChecked) return this.isLoggedIn
      if (!this.accessToken) { this.sessionChecked = true; return false }
      if (!restorePromise) {
        restorePromise = getCurrentUser().then((user) => {
          Object.assign(this, normalizeUserInfo(user), { sessionChecked: true })
          this.persistSession()
          return true
        }).finally(() => { restorePromise = null })
      }
      return restorePromise
    },
    persistSession() {
      localStorage.setItem(STORAGE_KEY, JSON.stringify({
        accessToken: this.accessToken, tokenType: this.tokenType, userId: this.userId,
        username: this.username, realName: this.realName, roleName: this.roleName,
        roleCode: this.roleCode, roleLevel: this.roleLevel, permissions: this.permissions,
        dataScope: this.dataScope, departmentId: this.departmentId,
        departmentName: this.departmentName, regionCode: this.regionCode,
      }))
    },
    clearSession() {
      Object.assign(this, {
        accessToken: '', tokenType: 'Bearer', userId: null, username: '', realName: '', roleName: '',
        roleCode: ROLE_CODE.QUERY_VIEWER, roleLevel: '', permissionLevel: 1, permissions: [],
        dataScope: null, departmentId: null, departmentName: '', regionCode: '', sessionChecked: true,
      })
      localStorage.removeItem(STORAGE_KEY)
      localStorage.removeItem('population_user')
      clearAllReferenceCache()
    },
    async logout() {
      if (!this.accessToken) { this.clearSession(); return }
      try { await logoutApi() } finally { this.clearSession() }
    },
    hasLevel(minLevel) { return this.permissionLevel >= minLevel },
    hasPermission(permission) { return !permission || checkPermission(this.permissions, permission) },
    hasAnyPermission(permissions) { return !permissions?.length || checkAnyPermission(this.permissions, permissions) },
    canAccess(meta) {
      if (!meta) return true
      if (meta.minLevel && !this.hasLevel(meta.minLevel)) return false
      return !meta.permission || this.hasPermission(meta.permission)
    },
  },
})
