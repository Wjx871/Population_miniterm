const { normalizeUser } = require('../../adapters/auth')
const { businessEntries } = require('../../adapters/business')
const { syncTabBar } = require('../../utils/tabBar.js')

Page({
  data: {
    user: {},
    entries: []
  },

  onLoad() {
    this.refreshEntries()
  },

  onShow() {
    syncTabBar(this, 'business')
    this.refreshEntries()
  },

  refreshEntries() {
    const user = normalizeUser(getApp().globalData.user)
    this.setData({ user, entries: businessEntries(user) })
  },

  open(event) {
    const url = event.currentTarget.dataset.url
    if (url) wx.navigateTo({ url })
  }
})
