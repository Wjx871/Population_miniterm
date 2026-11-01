const service = require('../../../services/permit')
const adapter = require('../../../adapters/mobile-business')
const { guard } = require('../../../utils/permission')
const { cleanQueryParams } = require('../../../utils/request-params')
const { messageOf } = require('../../../utils/error')

const STATUS_OPTIONS = [{ label: '全部状态', value: '' }, { label: '有效', value: 'ACTIVE' }, { label: '已过期', value: 'EXPIRED' }, { label: '已注销', value: 'CANCELLED' }]

Page({
  data: { personName: '', permitNo: '', statusIndex: 0, statusOptions: STATUS_OPTIONS, records: [], page: 0, size: 10, totalDisplay: '数量暂不可用', last: false, loading: false, loadingMore: false, error: '', expiringOnly: false },
  onLoad(options) { if (this._initialLoadStarted) return; this._initialLoadStarted = true; if (!guard('residence-permit:view')) return this.setData({ error: '当前账号无权查看居住证信息' }); this._authorized = true; this.setData({ expiringOnly: Boolean(options && options.expiring === '1') }); return this.load(true) },
  inputName(e) { this.setData({ personName: e.detail.value }) }, inputNo(e) { this.setData({ permitNo: e.detail.value }) },
  changeStatus(e) { this.setData({ statusIndex: Number(e.detail.value) }) },
  search() { return this.load(true) }, reset() { this.setData({ personName: '', permitNo: '', statusIndex: 0 }); return this.load(true) },
  async load(reset) {
    if (!this._authorized || this.data.loading || this.data.loadingMore || (!reset && this.data.last)) return
    const page = reset ? 0 : this.data.page + 1
    this.setData(reset ? { loading: true, error: '', last: false } : { loadingMore: true })
    try {
      const raw = this.data.expiringOnly ? await service.expiring(30) : await service.list(cleanQueryParams({ personName: this.data.personName.trim(), permitNo: this.data.permitNo.trim(), status: this.data.statusOptions[this.data.statusIndex].value, page, size: this.data.size }))
      const result = this.data.expiringOnly ? { records: (Array.isArray(raw) ? raw : []).map(adapter.permit), number: 0, last: true, totalDisplay: String((Array.isArray(raw) ? raw : []).length) } : adapter.permitPage(raw)
      this.setData({ records: reset ? result.records : this.data.records.concat(result.records), page: result.number, last: this.data.expiringOnly || result.last || result.records.length < this.data.size, totalDisplay: result.totalDisplay, error: '' })
    } catch (error) { if (reset) this.setData({ records: [], error: messageOf(error) }); else wx.showToast({ title: messageOf(error), icon: 'none' }) }
    finally { this.setData({ loading: false, loadingMore: false }); wx.stopPullDownRefresh() }
  },
  onPullDownRefresh() { return this.load(true) }, onReachBottom() { return this.load(false) },
  open(e) { wx.navigateTo({ url: `/pages/permits/detail/index?id=${e.currentTarget.dataset.id}` }) }
})
