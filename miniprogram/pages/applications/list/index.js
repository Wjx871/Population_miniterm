const service = require('../../../services/application')
const adapter = require('../../../adapters/application')
const { guard } = require('../../../utils/permission')
const { messageOf } = require('../../../utils/error')
const STATUS_OPTIONS = [{ label: '全部状态', value: '' }, { label: '草稿', value: 'DRAFT' }, { label: '审批中', value: 'UNDER_REVIEW' }, { label: '已通过', value: 'APPROVED' }, { label: '已驳回', value: 'REJECTED' }, { label: '已完成', value: 'COMPLETED' }, { label: '已撤回', value: 'WITHDRAWN' }]
Page({
  data: { applicationNo: '', statusIndex: 0, statusOptions: STATUS_OPTIONS, records: [], page: 0, size: 10, total: 0, last: false, loading: false, loadingMore: false, error: '' },
  onLoad() { if (guard('application:view')) this.load(true) },
  inputNo(e) { this.setData({ applicationNo: e.detail.value }) }, changeStatus(e) { this.setData({ statusIndex: Number(e.detail.value) }) },
  search() { this.load(true) }, reset() { this.setData({ applicationNo: '', statusIndex: 0 }); this.load(true) },
  async load(reset) { if (this.data.loading || this.data.loadingMore || (!reset && this.data.last)) return; const page = reset ? 0 : this.data.page + 1; this.setData(reset ? { loading: true, error: '' } : { loadingMore: true }); try { const status = this.data.statusOptions[this.data.statusIndex].value; const result = adapter.normalizePage(await service.list({ applicationNo: this.data.applicationNo.trim() || undefined, status: status || undefined, page, size: this.data.size })); this.setData({ records: reset ? result.records : this.data.records.concat(result.records), page: result.number, total: result.total, last: result.last || (page + 1) * this.data.size >= result.total, error: '' }) } catch (error) { if (reset) this.setData({ records: [], error: messageOf(error) }); else wx.showToast({ title: messageOf(error), icon: 'none' }) } finally { this.setData({ loading: false, loadingMore: false }); wx.stopPullDownRefresh() } },
  onPullDownRefresh() { this.load(true) }, onReachBottom() { this.load(false) }, open(e) { wx.navigateTo({ url: `/pages/applications/detail/index?id=${e.currentTarget.dataset.id}` }) }
})
