const authService = require('./services/auth')
const storage = require('./utils/storage')

App({
  globalData: { user: null, restoring: true },
  onLaunch() {
    this.restoreSession()
  },
  async restoreSession() {
    if (!storage.getToken()) {
      this.globalData.restoring = false
      this.toLogin()
      return
    }
    try {
      const user = await authService.me()
      storage.setUser(user)
      this.globalData.user = user
      const pages = getCurrentPages()
      const current = pages[pages.length - 1]
      if (!current || current.route === 'pages/login/index') wx.reLaunch({ url: '/pages/dashboard/index' })
    } catch (error) {
      if (error.statusCode !== 401) this.toLogin()
    } finally {
      this.globalData.restoring = false
    }
  },
  setUser(user) {
    this.globalData.user = user || null
  },
  toLogin() {
    const pages = getCurrentPages()
    const current = pages[pages.length - 1]
    if (!current || current.route !== 'pages/login/index') wx.reLaunch({ url: '/pages/login/index' })
  }
})
