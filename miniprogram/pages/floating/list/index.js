const service = require('../../../services/floating')
const adapter = require('../../../adapters/mobile-business')
const { guard } = require('../../../utils/permission')
const { cleanQueryParams } = require('../../../utils/request-params')
const { messageOf } = require('../../../utils/error')
const STATUS_OPTIONS = [{ label: '全部状态', value: '' }, { label: '有效登记', value: 'ACTIVE' }, { label: '已关闭', value: 'CLOSED' }, { label: '已到期', value: 'EXPIRED' }]
Page({
  data: { personName: '', identityNo: '', statusIndex: 0, statusOptions: STATUS_OPTIONS, records: [], page: 0, size: 10, totalDisplay: '数量暂不可用', last: false, loading: false, loadingMore: false, error: '' },
  onLoad() { if (this._initialLoadStarted) return; this._initialLoadStarted = true; if (!guard('floating:view')) return this.setData({ error: '当前账号无权查看流动人口信息' }); this._authorized = true; return this.load(true) },
  inputName(e) { this.setData({ personName: e.detail.value }) }, inputIdentity(e) { this.setData({ identityNo: e.detail.value }) }, changeStatus(e) { this.setData({ statusIndex: Number(e.detail.value) }) },
  search() { return this.load(true) }, reset() { this.setData({ personName: '', identityNo: '', statusIndex: 0 }); return this.load(true) },
  async load(reset) { if (!this._authorized || this.data.loading || this.data.loadingMore || (!reset && this.data.last)) return; const page = reset ? 0 : this.data.page + 1; this.setData(reset ? { loading: true, error: '', last: false } : { loadingMore: true }); try { const result = adapter.floatingPage(await service.list(cleanQueryParams({ personName: this.data.personName.trim(), identityNo: this.data.identityNo.trim(), status: this.data.statusOptions[this.data.statusIndex].value, page, size: this.data.size }))); this.setData({ records: reset ? result.records : this.data.records.concat(result.records), page: result.number, totalDisplay: result.totalDisplay, last: result.last || result.records.length < this.data.size, error: '' }) } catch (error) { if (reset) this.setData({ records: [], error: messageOf(error) }); else wx.showToast({ title: messageOf(error), icon: 'none' }) } finally { this.setData({ loading: false, loadingMore: false }); wx.stopPullDownRefresh() } },
  onPullDownRefresh() { return this.load(true) }, onReachBottom() { return this.load(false) }, open(e) { wx.navigateTo({ url: `/pages/floating/detail/index?id=${e.currentTarget.dataset.id}` }) }
})
