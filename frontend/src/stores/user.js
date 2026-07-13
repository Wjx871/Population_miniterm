import { defineStore } from 'pinia'
import { getCurrentUser, login as loginApi, logout as logoutApi } from '../api/auth.js'
import { ROLE_CODE, ROLE_LABEL } from '../constants/roles.js'
import { checkPermission, checkAnyPermission } from '../utils/permission.js'
import { normalizeLoginInfo, normalizeStoredSession, normalizeUserInfo } from './userNormalizer.js'

const STORAGE_KEY = 'population_user_v2'
let restorePromise = null

function loadStorageUser() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return {}
    return normalizeStoredSession(JSON.parse(raw))
  } catch {
    return {}
  }
}

export const useUserStore = defineStore('user', {
  state: () => {
    const saved = loadStorageUser()
    return {
      accessToken: saved.accessToken || '',
      tokenType: saved.tokenType || 'Bearer',
      userId: saved.userId || null,
      username: saved.username || '',
      realName: saved.realName || '',
      roleName: saved.roleName || '',
      roleCode: ROLE_CODE.QUERY_VIEWER,
      roleLevel: '',
      permissionLevel: 1,
      permissions: [],
      dataScope: null,
      departmentId: null,
      departmentName: '',
      regionCode: '',
      sessionChecked: !saved.accessToken,
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
      if (!this.accessToken) {
        this.sessionChecked = true
        return false
      }
      if (!restorePromise) {
        restorePromise = getCurrentUser()
          .then((user) => {
            Object.assign(this, normalizeUserInfo(user), { sessionChecked: true })
            this.persistSession()
            return true
          })
          .finally(() => { restorePromise = null })
      }
      return restorePromise
    },

    persistSession() {
      localStorage.setItem(STORAGE_KEY, JSON.stringify({
        accessToken: this.accessToken,
        tokenType: this.tokenType,
        userId: this.userId,
        username: this.username,
        realName: this.realName,
        roleName: this.roleName,
        roleCode: this.roleCode,
        roleLevel: this.roleLevel,
        permissions: this.permissions,
        dataScope: this.dataScope,
        departmentId: this.departmentId,
        departmentName: this.departmentName,
        regionCode: this.regionCode,
      }))
    },

    clearSession() {
      this.accessToken = ''
      this.tokenType = 'Bearer'
      this.userId = null
      this.username = ''
      this.realName = ''
      this.roleName = ''
      this.roleCode = ROLE_CODE.QUERY_VIEWER
      this.roleLevel = ''
      this.permissionLevel = 1
      this.permissions = []
      this.dataScope = null
      this.departmentId = null
      this.departmentName = ''
      this.regionCode = ''
      this.sessionChecked = true
      localStorage.removeItem(STORAGE_KEY)
      localStorage.removeItem('population_user')
      Object.keys(localStorage)
        .filter((key) => key.startsWith('population_reference_') || key.startsWith('population_cache_'))
        .forEach((key) => localStorage.removeItem(key))
    },

    async logout() {
      if (!this.accessToken) {
        this.clearSession()
        return
      }
      try {
        await logoutApi()
      } finally {
        this.clearSession()
      }
    },

    hasLevel(minLevel) {
      return this.permissionLevel >= minLevel
    },

    hasPermission(permission) {
      if (!permission) return true
      return checkPermission(this.permissions, permission)
    },

    hasAnyPermission(permissions) {
      if (!permissions || permissions.length === 0) return true
      return checkAnyPermission(this.permissions, permissions)
    },

    canAccess(meta) {
      if (!meta) return true
      if (meta.minLevel && !this.hasLevel(meta.minLevel)) return false
      if (meta.permission && !this.hasPermission(meta.permission)) return false
      return true
    }
  },
})
