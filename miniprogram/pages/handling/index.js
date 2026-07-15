const dashboard = require('../../services/dashboard')
const { normalizeUser } = require('../../adapters/auth')
const { handlingEntries, normalizePendingSummary } = require('../../adapters/handling')
const { can } = require('../../utils/permission')
const { syncTabBar } = require('../../utils/tabBar.js')

Page({
  data: {
    user: {},
    entries: [],
    pendingSummary: normalizePendingSummary(null),
    summaryLoading: false,
    summaryError: ''
  },

  onLoad() {
    const user = this.refreshEntries()
    if (can(user, 'approval:view')) return this.loadSummary()
  },

  onShow() {
    syncTabBar(this, 'handling')
    this.refreshEntries()
  },

  refreshEntries() {
    const user = normalizeUser(getApp().globalData.user)
    this.setData({ user, entries: handlingEntries(user) })
    return user
  },

  async loadSummary() {
    if (this.data.summaryLoading || !can(this.data.user, 'approval:view')) return
    this.setData({ summaryLoading: true, summaryError: '' })
    try {
      const raw = await dashboard.overview()
      this.setData({ pendingSummary: normalizePendingSummary(raw) })
    } catch (error) {
      this.setData({
        pendingSummary: normalizePendingSummary(null),
        summaryError: '待办数量暂时无法加载，请重新加载'
      })
    } finally {
      this.setData({ summaryLoading: false })
      wx.stopPullDownRefresh()
    }
  },

  onPullDownRefresh() {
    if (!can(this.data.user, 'approval:view')) {
      wx.stopPullDownRefresh()
      return
    }
    return this.loadSummary()
  },

  open(event) {
    const url = event.currentTarget.dataset.url
    if (url) wx.navigateTo({ url })
  }
})
