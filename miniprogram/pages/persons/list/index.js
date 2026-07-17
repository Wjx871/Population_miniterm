const service = require('../../../services/person')
const adapter = require('../../../adapters/person')
const { guard } = require('../../../utils/permission')
const { cleanQueryParams } = require('../../../utils/request-params')
const { messageOf } = require('../../../utils/error')

const STATUS_OPTIONS = Object.freeze([
  { label: '全部状态', value: '' },
  { label: '正常', value: 'NORMAL' },
  { label: '已迁出', value: 'MOVED_OUT' },
  { label: '已注销', value: 'CANCELLED' },
  { label: '已死亡', value: 'DECEASED' }
])

Page({
  data: {
    name: '',
    idCard: '',
    status: '',
    statusIndex: 0,
    statusOptions: STATUS_OPTIONS,
    filtersExpanded: false,
    records: [],
    page: 0,
    size: 10,
    total: null,
    totalAvailable: false,
    totalDisplay: '数量暂不可用',
    last: false,
    loading: false,
    loadingMore: false,
    error: '',
    errorType: ''
  },

  onLoad() {
    if (!guard('population:view')) {
      this.setData({ error: '当前账号无权查看人口档案', errorType: 'forbidden' })
      return
    }
    this._authorized = true
    return this.load(true)
  },

  inputName(event) { this.setData({ name: event.detail.value }) },
  inputIdCard(event) { this.setData({ idCard: event.detail.value }) },
  toggleFilters() { this.setData({ filtersExpanded: !this.data.filtersExpanded }) },
  changeStatus(event) {
    const statusIndex = Number(event.detail.value)
    const option = this.data.statusOptions[statusIndex] || this.data.statusOptions[0]
    this.setData({ statusIndex, status: option.value })
  },

  search() { return this.load(true) },
  reset() {
    this.setData({ name: '', idCard: '', status: '', statusIndex: 0 })
    return this.load(true)
  },

  async load(reset) {
    if (!this._authorized) return
    if (this.data.loading || this.data.loadingMore || (!reset && this.data.last)) return
    const page = reset ? 0 : this.data.page + 1
    const params = cleanQueryParams({
      name: this.data.name.trim(),
      idCard: this.data.idCard.trim(),
      status: this.data.status,
      page,
      size: this.data.size
    })
    this.setData(reset
      ? { loading: true, error: '', errorType: '', page: 0, last: false }
      : { loadingMore: true })

    try {
      const result = adapter.normalizePage(await service.list(params))
      this.setData({
        records: reset ? result.records : this.data.records.concat(result.records),
        page: result.number,
        total: result.total,
        totalAvailable: result.totalAvailable,
        totalDisplay: result.totalDisplay,
        last: result.last || result.records.length < this.data.size,
        error: ''
      })
    } catch (error) {
      if (reset) {
        this.setData({
          records: [],
          total: null,
          totalAvailable: false,
          totalDisplay: '数量暂不可用',
          error: messageOf(error),
          errorType: ''
        })
      } else {
        wx.showToast({ title: messageOf(error), icon: 'none' })
      }
    } finally {
      this.setData({ loading: false, loadingMore: false })
      wx.stopPullDownRefresh()
    }
  },

  onPullDownRefresh() { return this.load(true) },
  onReachBottom() { return this.load(false) },
  open(event) {
    wx.navigateTo({ url: `/pages/persons/detail/index?id=${event.currentTarget.dataset.id}` })
  }
})
