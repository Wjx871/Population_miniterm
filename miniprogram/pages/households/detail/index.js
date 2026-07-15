const service = require('../../../services/household')
const adapter = require('../../../adapters/household')
const { guard } = require('../../../utils/permission')
const { messageOf } = require('../../../utils/error')

Page({
  data: {
    id: null,
    household: null,
    members: [],
    membersLoaded: false,
    membersLoading: false,
    membersError: '',
    loading: true,
    error: '',
    errorType: ''
  },

  onLoad(options) {
    this.setData({ id: options.id })
    if (!guard('household:view')) {
      this.setData({ loading: false, error: '当前账号无权查看家庭户档案', errorType: 'forbidden' })
      return
    }
    this._authorized = true
    return this.load()
  },

  async load() {
    if (!this._authorized) return
    if (this._loading) return
    this._loading = true
    this.setData({ loading: true, error: '', errorType: '', membersError: '' })
    const [detailResult, membersResult] = await Promise.allSettled([
      service.detail(this.data.id),
      service.members(this.data.id)
    ])

    if (detailResult.status === 'rejected') {
      this.setData({ household: null, loading: false, error: messageOf(detailResult.reason) })
      this._loading = false
      return
    }

    const household = adapter.normalize(detailResult.value)
    this.setData({
      household,
      members: membersResult.status === 'fulfilled'
        ? (Array.isArray(membersResult.value) ? membersResult.value : []).map(adapter.member)
        : [],
      membersLoaded: membersResult.status === 'fulfilled',
      membersError: membersResult.status === 'rejected' ? messageOf(membersResult.reason) : '',
      loading: false
    })
    this._loading = false
  },

  async retryMembers() {
    if (this.data.membersLoading) return
    this.setData({ membersLoading: true, membersError: '' })
    try {
      const members = await service.members(this.data.id)
      this.setData({
        members: (Array.isArray(members) ? members : []).map(adapter.member),
        membersLoaded: true
      })
    } catch (error) {
      this.setData({ members: [], membersLoaded: false, membersError: messageOf(error) })
    } finally {
      this.setData({ membersLoading: false })
    }
  },

  openPerson(event) {
    wx.navigateTo({ url: `/pages/persons/detail/index?id=${event.currentTarget.dataset.id}` })
  }
})
