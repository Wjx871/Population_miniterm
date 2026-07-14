const service = require('../../../services/household')
const adapter = require('../../../adapters/household')
const { guard } = require('../../../utils/permission')
const { messageOf } = require('../../../utils/error')
Page({
  data: { householdNo: '', headPersonName: '', status: '', records: [], page: 0, size: 10, total: 0, last: false, loading: false, loadingMore: false, error: '' },
  onLoad() { if (guard('household:view')) this.load(true) },
  inputNo(e) { this.setData({ householdNo: e.detail.value }) }, inputHead(e) { this.setData({ headPersonName: e.detail.value }) },
  search() { this.load(true) }, reset() { this.setData({ householdNo: '', headPersonName: '', status: '' }); this.load(true) },
  async load(reset) { if (this.data.loading || this.data.loadingMore || (!reset && this.data.last)) return; const page = reset ? 0 : this.data.page + 1; this.setData(reset ? { loading: true, error: '' } : { loadingMore: true }); try { const result = adapter.normalizePage(await service.list({ householdNo: this.data.householdNo.trim() || undefined, headPersonName: this.data.headPersonName.trim() || undefined, status: this.data.status || undefined, page, size: this.data.size })); this.setData({ records: reset ? result.records : this.data.records.concat(result.records), page: result.number, total: result.total, last: result.last || (page + 1) * this.data.size >= result.total, error: '' }) } catch (error) { if (reset) this.setData({ records: [], error: messageOf(error) }); else wx.showToast({ title: messageOf(error), icon: 'none' }) } finally { this.setData({ loading: false, loadingMore: false }); wx.stopPullDownRefresh() } },
  onPullDownRefresh() { this.load(true) }, onReachBottom() { this.load(false) }, open(e) { wx.navigateTo({ url: `/pages/households/detail/index?id=${e.currentTarget.dataset.id}` }) }
})
