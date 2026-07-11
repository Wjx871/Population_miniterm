import { defineStore } from 'pinia'
import { login as loginApi } from '../api/auth'
import { ROLE_CODE, ROLE_LABEL } from '../constants/roles'
import { normalizeRoleCode, resolvePermissions, getRoleLevel, checkPermission, checkAnyPermission } from '../utils/permission'

const STORAGE_KEY = 'population_user'

function loadStorageUser() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return {}
    const parsed = JSON.parse(raw)
    // On load, always re-normalize to ensure integrity
    const roleCode = normalizeRoleCode(parsed.roleCode, parsed.roleName)
    
    // 角色无法识别时，强制使用 NORMAL_USER 并忽略缓存的 permissions
    if (roleCode === ROLE_CODE.NORMAL_USER && parsed.roleCode !== ROLE_CODE.NORMAL_USER) {
      parsed.permissions = undefined
    }
    
    parsed.roleCode = roleCode
    parsed.permissionLevel = getRoleLevel(roleCode)
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
      roleCode: saved.roleCode || ROLE_CODE.NORMAL_USER,
      permissionLevel: saved.permissionLevel || 1,
      permissions: saved.permissions || [],
    }
  },

  getters: {
    isLoggedIn: (state) => Boolean(state.accessToken),
    displayName: (state) => state.realName || state.username || '用户',
    roleLabel: (state) => ROLE_LABEL[state.roleCode] || state.roleName || '普通用户',
    isSuperAdmin: (state) => state.roleCode === ROLE_CODE.SUPER_ADMIN,
  },

  actions: {
    setLoginInfo(loginVO) {
      this.accessToken = loginVO.accessToken
      this.tokenType = loginVO.tokenType || 'Bearer'
      this.userId = loginVO.userId
      this.username = loginVO.username
      this.realName = loginVO.realName
      this.roleName = loginVO.roleName

      // 角色归一化与权限计算
      this.roleCode = normalizeRoleCode(loginVO.roleCode, loginVO.roleName)
      this.permissionLevel = getRoleLevel(this.roleCode)
      this.permissions = resolvePermissions(this.roleCode, loginVO.permissions)

      localStorage.setItem(STORAGE_KEY, JSON.stringify({
        accessToken: this.accessToken,
        tokenType: this.tokenType,
        userId: this.userId,
        username: this.username,
        realName: this.realName,
        roleName: this.roleName,
        roleCode: this.roleCode,
        permissions: this.permissions // Cache permissions array, but we re-resolve on load just in case
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
      this.roleCode = ROLE_CODE.NORMAL_USER
      this.permissionLevel = 1
      this.permissions = []
      localStorage.removeItem(STORAGE_KEY)
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
