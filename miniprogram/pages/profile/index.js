const auth = require('../../services/auth')
const dashboard = require('../../services/dashboard')
const storage = require('../../utils/storage')
const { normalizeUser } = require('../../adapters/auth')
const { messageOf } = require('../../utils/error')
const { syncTabBar, resetTabBar } = require('../../utils/tabBar.js')
Page({
  data: { user: {}, health: null, loading: false, error: '', loggingOut: false },
  onShow() { syncTabBar(this, 'profile'); this.setData({ user: normalizeUser(getApp().globalData.user) }); this.loadHealth() },
  async loadHealth() { this.setData({ loading: true, error: '' }); try { const h = await dashboard.health(); this.setData({ health: { database: h.database === 'UP' ? '数据库正常' : '数据库异常', cache: h.cacheMode === 'REDIS' ? 'Redis 缓存' : 'MySQL 回源', redis: h.redisStatus || '—' } }) } catch (e) { this.setData({ error: messageOf(e), health: null }) } finally { this.setData({ loading: false }) } },
  async logout() {
    const confirmed = await new Promise((resolve) => wx.showModal({ title: '退出登录', content: '确定退出当前账号吗？', success: (r) => resolve(r.confirm) }))
    if (!confirmed) return
    this.setData({ loggingOut: true })
    try { await auth.logout() } catch (error) { if (error.statusCode !== 401) wx.showToast({ title: messageOf(error), icon: 'none' }) }
    finally { storage.clearSession(); getApp().setUser(null); resetTabBar(this); this.setData({ loggingOut: false }); wx.reLaunch({ url: '/pages/login/index' }) }
  }
})
