const { normalizeProfile } = require('../../../adapters/profile')
Page({ data: { user: { duties: [], features: [] } }, onShow() { this.setData({ user: normalizeProfile(getApp().globalData.user) }) }, open(e) { const url = e.currentTarget.dataset.url; if (url) wx.navigateTo({ url }) } })
