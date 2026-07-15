const service = require('../../../services/household')
const adapter = require('../../../adapters/household')
const { guard } = require('../../../utils/permission')
const { cleanQueryParams } = require('../../../utils/request-params')
const { messageOf } = require('../../../utils/error')

const TYPE_OPTIONS = Object.freeze([
  { label: '全部户口类型', value: '' },
  { label: '家庭户', value: 'FAMILY' },
  { label: '集体户', value: 'COLLECTIVE' }
])
const STATUS_OPTIONS = Object.freeze([
  { label: '全部状态', value: '' },
  { label: '正常', value: 'ACTIVE' },
  { label: '待注销', value: 'PENDING_CANCELLATION' },
  { label: '已注销', value: 'CANCELLED' },
  { label: '已停用', value: 'DISABLED' }
])

Page({
  data: {
    householdNo: '',
    headPersonName: '',
    householdType: '',
    typeIndex: 0,
    typeOptions: TYPE_OPTIONS,
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
    if (!guard('household:view')) {
      this.setData({ error: '当前账号无权查看家庭户档案', errorType: 'forbidden' })
      return
    }
    this._authorized = true
    return this.load(true)
  },

  inputNo(event) { this.setData({ householdNo: event.detail.value }) },
  inputHead(event) { this.setData({ headPersonName: event.detail.value }) },
  toggleFilters() { this.setData({ filtersExpanded: !this.data.filtersExpanded }) },
  changeType(event) {
    const typeIndex = Number(event.detail.value)
    const option = this.data.typeOptions[typeIndex] || this.data.typeOptions[0]
    this.setData({ typeIndex, householdType: option.value })
  },
  changeStatus(event) {
    const statusIndex = Number(event.detail.value)
    const option = this.data.statusOptions[statusIndex] || this.data.statusOptions[0]
    this.setData({ statusIndex, status: option.value })
  },

  search() { return this.load(true) },
  reset() {
    this.setData({
      householdNo: '',
      headPersonName: '',
      householdType: '',
      typeIndex: 0,
      status: '',
      statusIndex: 0
    })
    return this.load(true)
  },

  async load(reset) {
    if (!this._authorized) return
    if (this.data.loading || this.data.loadingMore || (!reset && this.data.last)) return
    const page = reset ? 0 : this.data.page + 1
    const params = cleanQueryParams({
      householdNo: this.data.householdNo.trim(),
      headPersonName: this.data.headPersonName.trim(),
      householdType: this.data.householdType,
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
    wx.navigateTo({ url: `/pages/households/detail/index?id=${event.currentTarget.dataset.id}` })
  }
})
