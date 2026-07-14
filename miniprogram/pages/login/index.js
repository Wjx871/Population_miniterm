const auth = require('../../services/auth')
const storage = require('../../utils/storage')
const { normalizeUser } = require('../../adapters/auth')
const { messageOf } = require('../../utils/error')
const { reset401Guard } = require('../../services/request')

Page({
  data: { username: '', password: '', showPassword: false, remember: true, loading: false, error: '' },
  onLoad() { reset401Guard(); this.setData({ username: storage.getRememberedUsername() }) },
  inputUsername(e) { this.setData({ username: e.detail.value, error: '' }) },
  inputPassword(e) { this.setData({ password: e.detail.value, error: '' }) },
  togglePassword() { this.setData({ showPassword: !this.data.showPassword }) },
  toggleRemember() { this.setData({ remember: !this.data.remember }) },
  async submit() {
    const username = this.data.username.trim()
    if (!username || !this.data.password) { this.setData({ error: '请输入用户名和密码' }); return }
    this.setData({ loading: true, error: '' })
    try {
      const session = await auth.login(username, this.data.password)
      storage.setToken(session.token)
      const user = normalizeUser(await auth.me())
      storage.setUser(user)
      this.data.remember ? storage.rememberUsername(username) : storage.rememberUsername('')
      getApp().setUser(user)
      this.setData({ password: '' })
      wx.reLaunch({ url: '/pages/dashboard/index' })
    } catch (error) {
      if (error.statusCode !== 401) storage.clearSession()
      this.setData({ error: error.statusCode === 0 ? '后端服务不可达，请检查服务地址和网络' : messageOf(error), password: '' })
    } finally { this.setData({ loading: false }) }
  }
})
