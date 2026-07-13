import { defineStore } from 'pinia'
import { login as loginApi } from '../api/auth.js'
import { ROLE_CODE, ROLE_LABEL, parseRoleLevel } from '../constants/roles.js'
import { normalizeRoleCode, resolvePermissions, checkPermission, checkAnyPermission } from '../utils/permission.js'

const STORAGE_KEY = 'population_user_v2'

function loadStorageUser() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return {}
    const parsed = JSON.parse(raw)
    // On load, always re-normalize to ensure integrity
    const roleCode = normalizeRoleCode(parsed.roleCode, parsed.roleName)
    
    parsed.roleCode = roleCode
    parsed.permissions = resolvePermissions(roleCode, parsed.permissions)
    return parsed
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
      roleLevel: saved.roleLevel || 'L1',
      permissionLevel: saved.permissionLevel || 1,
      permissions: saved.permissions || [],
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
      const user = loginVO.user || loginVO
      this.accessToken = loginVO.token || loginVO.accessToken || ''
      this.tokenType = loginVO.tokenType || 'Bearer'
      this.userId = user.userId
      this.username = user.username
      this.realName = user.realName
      this.roleName = user.roleName
      this.roleLevel = user.roleLevel || 'L1'

      // 角色归一化与权限计算
      this.roleCode = normalizeRoleCode(user.roleCode, user.roleName)
      
      const parsedLevel = parseRoleLevel(this.roleLevel)
      this.permissionLevel = parsedLevel !== null ? parsedLevel : 1
      
      const hasApiPermissions = Array.isArray(user.permissions)
      this.permissions = hasApiPermissions ? [...user.permissions] : []

      localStorage.setItem(STORAGE_KEY, JSON.stringify({
        accessToken: this.accessToken,
        tokenType: this.tokenType,
        userId: this.userId,
        username: this.username,
        realName: this.realName,
        roleName: this.roleName,
        roleCode: this.roleCode,
        roleLevel: this.roleLevel,
        permissionLevel: this.permissionLevel,
        permissions: this.permissions
      }))
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
      this.roleLevel = 'L1'
      this.permissionLevel = 1
      this.permissions = []
      localStorage.removeItem(STORAGE_KEY)
      // Clean up legacy v1 storage if it exists
      localStorage.removeItem('population_user')
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
