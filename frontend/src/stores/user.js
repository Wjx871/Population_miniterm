import { defineStore } from 'pinia'
import { login as loginApi, logout as logoutApi } from '../api/auth'

const STORAGE_KEY = 'population_user'

function loadStorageUser() {
  try {
    return JSON.parse(localStorage.getItem(STORAGE_KEY) || '{}')
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
      roleCode: saved.roleCode || '',
      roleLevel: saved.roleLevel || '',
      dataScope: saved.dataScope || '',
      departmentId: saved.departmentId || null,
      departmentName: saved.departmentName || '',
      permissions: saved.permissions || [],
    }
  },

  getters: {
    isLoggedIn: (state) => Boolean(state.accessToken),
    displayName: (state) => state.realName || state.username || '用户',
  },

  actions: {
    setLoginInfo(loginVO) {
      const user = loginVO.user || {}
      this.accessToken = loginVO.token
      this.tokenType = loginVO.tokenType || 'Bearer'
      this.userId = user.userId
      this.username = user.username
      this.realName = user.realName
      this.roleName = user.roleName
      this.roleCode = user.roleCode
      this.roleLevel = user.roleLevel
      this.dataScope = user.dataScope
      this.departmentId = user.departmentId
      this.departmentName = user.departmentName
      this.permissions = user.permissions || []
      localStorage.setItem(STORAGE_KEY, JSON.stringify(this.$state))
    },

    async login(form) {
      const loginVO = await loginApi(form)
      this.setLoginInfo(loginVO)
      return loginVO
    },

    clearAuth() {
      localStorage.removeItem(STORAGE_KEY)
      this.$reset()
    },

    async logout() {
      try {
        if (this.accessToken) await logoutApi()
      } finally {
        this.clearAuth()
      }
    },
  },
})
