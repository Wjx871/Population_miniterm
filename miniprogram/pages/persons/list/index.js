const service = require('../../../services/person')
const adapter = require('../../../adapters/person')
const { guard } = require('../../../utils/permission')
const { messageOf } = require('../../../utils/error')
Page({
  data: { name: '', idCard: '', records: [], page: 0, size: 10, total: 0, last: false, loading: false, loadingMore: false, error: '' },
  onLoad() { if (guard('population:view')) this.load(true) },
  inputName(e) { this.setData({ name: e.detail.value }) }, inputIdCard(e) { this.setData({ idCard: e.detail.value }) },
  search() { this.load(true) }, reset() { this.setData({ name: '', idCard: '' }); this.load(true) },
  async load(reset) {
    if (this.data.loading || this.data.loadingMore || (!reset && this.data.last)) return
    const page = reset ? 0 : this.data.page + 1
    this.setData(reset ? { loading: true, error: '' } : { loadingMore: true })
    try {
      const result = adapter.normalizePage(await service.list({ name: this.data.name.trim() || undefined, idCard: this.data.idCard.trim() || undefined, page, size: this.data.size }))
      this.setData({ records: reset ? result.records : this.data.records.concat(result.records), page: result.number, total: result.total, last: result.last || (page + 1) * this.data.size >= result.total, error: '' })
    } catch (error) { if (reset) this.setData({ records: [], error: messageOf(error) }); else wx.showToast({ title: messageOf(error), icon: 'none' }) }
    finally { this.setData({ loading: false, loadingMore: false }); wx.stopPullDownRefresh() }
  },
  onPullDownRefresh() { this.load(true) }, onReachBottom() { this.load(false) },
  open(e) { wx.navigateTo({ url: `/pages/persons/detail/index?id=${e.currentTarget.dataset.id}` }) }
})
