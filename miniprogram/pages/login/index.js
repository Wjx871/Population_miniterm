const auth = require('../../services/auth')
const storage = require('../../utils/storage')
const { normalizeUser } = require('../../adapters/auth')
const { validateCredentials, safeLoginError } = require('../../utils/login')
const { reset401Guard } = require('../../services/request')

Page({
  data: {
    username: '',
    password: '',
    showPassword: false,
    remember: true,
    loading: false,
    error: ''
  },

  onLoad() {
    reset401Guard()
    this.setData({ username: storage.getRememberedUsername() })
  },

  inputUsername(event) {
    this.setData({ username: event.detail.value, error: '' })
  },

  inputPassword(event) {
    this.setData({ password: event.detail.value, error: '' })
  },

  togglePassword() {
    this.setData({ showPassword: !this.data.showPassword })
  },

  toggleRemember() {
    this.setData({ remember: !this.data.remember })
  },

  async submit() {
    if (this.data.loading) return
    const username = this.data.username.trim()
    const validationError = validateCredentials(username, this.data.password)
    if (validationError) {
      this.setData({ error: validationError })
      return
    }

    this.setData({ loading: true, error: '' })
    try {
      const session = await auth.login(username, this.data.password)
      storage.setToken(session.token)
      const user = normalizeUser(await auth.me())
      storage.setUser(user)
      storage.rememberUsername(this.data.remember ? username : '')
      getApp().setUser(user)
      this.setData({ password: '' })
      wx.reLaunch({ url: '/pages/dashboard/index' })
    } catch (error) {
      if (error.statusCode !== 401) storage.clearSession()
      this.setData({ error: safeLoginError(error), password: '' })
    } finally {
      this.setData({ loading: false })
    }
  }
})
