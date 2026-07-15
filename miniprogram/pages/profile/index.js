const auth = require('../../services/auth')
const storage = require('../../utils/storage')
const { normalizeProfile } = require('../../adapters/profile')
const { messageOf } = require('../../utils/error')
const { syncTabBar, resetTabBar } = require('../../utils/tabBar.js')
Page({
  data: { user: { duties: [], features: [] }, appInfo: { name: '人口信息管理系统', version: 'V1.0.0' }, loggingOut: false },
  onShow() { syncTabBar(this, 'profile'); this.setData({ user: normalizeProfile(getApp().globalData.user) }) },
  openFeature(e) { const url = e.currentTarget.dataset.url; if (url) wx.navigateTo({ url }) },
  async logout() {
    const confirmed = await new Promise((resolve) => wx.showModal({ title: '退出登录', content: '确定退出当前账号吗？', success: (r) => resolve(r.confirm) }))
    if (!confirmed) return
    this.setData({ loggingOut: true })
    try { await auth.logout() } catch (error) { if (error.statusCode !== 401) wx.showToast({ title: messageOf(error), icon: 'none' }) }
    finally { storage.clearSession(); getApp().setUser(null); resetTabBar(this); this.setData({ loggingOut: false }); wx.reLaunch({ url: '/pages/login/index' }) }
  }
})
