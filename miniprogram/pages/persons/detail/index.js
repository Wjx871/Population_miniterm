const service = require('../../../services/person')
const adapter = require('../../../adapters/person')
const { guard } = require('../../../utils/permission')
const { messageOf } = require('../../../utils/error')
Page({
  data: { id: null, person: null, household: null, loading: true, error: '' },
  onLoad(options) { this.setData({ id: options.id }); if (guard('population:view')) this.load() },
  async load() { this.setData({ loading: true, error: '' }); try { const [raw, profile] = await Promise.all([service.detail(this.data.id), service.profile(this.data.id).catch((error) => { if (error.statusCode === 403 || error.statusCode === 404) return null; throw error })]); this.setData({ person: adapter.normalize(raw), household: profile && profile.currentHousehold || null }) } catch (error) { this.setData({ error: messageOf(error), person: null }) } finally { this.setData({ loading: false }) } },
  openHousehold() { if (this.data.household && this.data.household.householdId) wx.navigateTo({ url: `/pages/households/detail/index?id=${this.data.household.householdId}` }) }
})
