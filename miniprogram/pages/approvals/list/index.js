const service = require('../../../services/approval')
const adapter = require('../../../adapters/application')
const { guard } = require('../../../utils/permission')
const { messageOf } = require('../../../utils/error')
Page({
  data: { tab: 'pending', records: [], loading: true, error: '' },
  onLoad() {
    if (this._initialLoadStarted) return
    this._initialLoadStarted = true
    if (guard('approval:view')) return this.load()
  },
  switchTab(e) {
    const tab = e.currentTarget.dataset.tab
    if (tab === this.data.tab || this.data.loading) return
    this.setData({ tab })
    return this.load()
  },
  async load() {
    if (this.data.loading && this._requestActive) return
    this._requestActive = true
    this.setData({ loading: true, error: '' })
    try {
      const records = this.data.tab === 'pending' ? await service.pending() : await service.processed()
      this.setData({ records: (records || []).map(adapter.approval) })
    } catch (error) {
      this.setData({ records: [], error: messageOf(error) })
    } finally {
      this._requestActive = false
      this.setData({ loading: false })
      wx.stopPullDownRefresh()
    }
  },
  onPullDownRefresh() { return this.load() },
  open(e) { wx.navigateTo({ url: `/pages/approvals/detail/index?id=${e.currentTarget.dataset.id}` }) }
})
