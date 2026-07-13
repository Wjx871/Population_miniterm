import { defineStore } from 'pinia'
import { login as loginApi } from '../api/auth.js'
import { ROLE_CODE, ROLE_LABEL } from '../constants/roles.js'
import { checkPermission, checkAnyPermission } from '../utils/permission.js'
import { normalizeLoginUser, normalizeStoredUser } from './userNormalizer.js'
import { clearAllReferenceCache } from '../services/referenceDataCache.js'

export const STORAGE_KEY = 'population_user_v2'

function loadStorageUser() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return {}
    return normalizeStoredUser(JSON.parse(raw))
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
      roleCode: saved.roleCode || ROLE_CODE.QUERY_VIEWER,
      roleLevel: saved.roleLevel || '',
      permissionLevel: saved.permissionLevel || 1,
      permissions: saved.permissions || [],
      dataScope: saved.dataScope || null,
      departmentId: saved.departmentId || null,
      departmentName: saved.departmentName || '',
      regionCode: saved.regionCode || '',
    }
  },

  getters: {
    isLoggedIn: (state) => Boolean(state.accessToken),
    displayName: (state) => state.realName || state.username || '用户',
    roleLabel: (state) => ROLE_LABEL[state.roleCode] || state.roleName || '查询统计人员',
    isSuperAdmin: (state) => state.roleCode === ROLE_CODE.SYSTEM_ADMIN,
  },

  actions: {
    setLoginInfo(loginVO) {
      const normalized = normalizeLoginUser(loginVO)
      Object.assign(this, normalized)
      
      localStorage.setItem(STORAGE_KEY, JSON.stringify(normalized))
    },

    async login(form) {
      const loginVO = await loginApi(form)
      this.setLoginInfo(loginVO)
      return loginVO
    },

    logout() {
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
      
      localStorage.removeItem(STORAGE_KEY)
      // Clean up legacy v1 storage if it exists
      localStorage.removeItem('population_user')
      clearAllReferenceCache()
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
