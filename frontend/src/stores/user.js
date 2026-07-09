import { defineStore } from 'pinia'
import { login as loginApi } from '../api/auth'

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
    }
  },

  getters: {
    isLoggedIn: (state) => Boolean(state.accessToken),
    displayName: (state) => state.realName || state.username || '用户',
  },

  actions: {
    setLoginInfo(loginVO) {
      this.accessToken = loginVO.accessToken
      this.tokenType = loginVO.tokenType || 'Bearer'
      this.userId = loginVO.userId
      this.username = loginVO.username
      this.realName = loginVO.realName
      this.roleName = loginVO.roleName

      localStorage.setItem(STORAGE_KEY, JSON.stringify({
        accessToken: this.accessToken,
        tokenType: this.tokenType,
        userId: this.userId,
        username: this.username,
        realName: this.realName,
        roleName: this.roleName,
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
      localStorage.removeItem(STORAGE_KEY)
    },
  },
})
