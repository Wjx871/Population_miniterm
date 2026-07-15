const service = require('../../../services/person')
const adapter = require('../../../adapters/person')
const { guard } = require('../../../utils/permission')
const { messageOf } = require('../../../utils/error')

Page({
  data: {
    id: null,
    person: null,
    household: null,
    residence: null,
    profileLoaded: false,
    profileLoading: false,
    profileError: '',
    loading: true,
    error: '',
    errorType: ''
  },

  onLoad(options) {
    this.setData({ id: options.id })
    if (!guard('population:view')) {
      this.setData({ loading: false, error: '当前账号无权查看人口档案', errorType: 'forbidden' })
      return
    }
    this._authorized = true
    return this.load()
  },

  async load() {
    if (!this._authorized) return
    if (this._loading) return
    this._loading = true
    this.setData({ loading: true, error: '', errorType: '', profileError: '' })
    const [detailResult, profileResult] = await Promise.allSettled([
      service.detail(this.data.id),
      service.profile(this.data.id)
    ])

    if (detailResult.status === 'rejected') {
      this.setData({
        loading: false,
        person: null,
        error: messageOf(detailResult.reason)
      })
      this._loading = false
      return
    }

    const profile = profileResult.status === 'fulfilled' ? profileResult.value : null
    const person = adapter.normalize(detailResult.value, profile && profile.person)
    this.setData({
      person,
      household: adapter.normalizeHousehold(profile && profile.currentHousehold),
      residence: adapter.normalizeResidence(profile && profile.currentResidence, person.rawAddress),
      profileLoaded: profileResult.status === 'fulfilled',
      profileError: profileResult.status === 'rejected' ? messageOf(profileResult.reason) : '',
      loading: false
    })
    this._loading = false
  },

  async retryProfile() {
    if (this.data.profileLoading) return
    this.setData({ profileLoading: true, profileError: '' })
    try {
      const profile = await service.profile(this.data.id)
      const person = adapter.normalize(this.data.person, profile && profile.person)
      this.setData({
        person,
        household: adapter.normalizeHousehold(profile && profile.currentHousehold),
        residence: adapter.normalizeResidence(profile && profile.currentResidence, person.rawAddress),
        profileLoaded: true
      })
    } catch (error) {
      this.setData({ profileLoaded: false, profileError: messageOf(error) })
    } finally {
      this.setData({ profileLoading: false })
    }
  },

  openHousehold() {
    if (this.data.household && this.data.household.householdId) {
      wx.navigateTo({ url: `/pages/households/detail/index?id=${this.data.household.householdId}` })
    }
  }
})
