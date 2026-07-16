const service = require('../../../services/permit')
const adapter = require('../../../adapters/mobile-business')
const { guard, can } = require('../../../utils/permission')
const { messageOf } = require('../../../utils/error')
Page({
  data: { id: '', permit: null, canApply: false, canViewApplication: false, loading: true, error: '' },
  onLoad(options) { this.setData({ id: options.id }); if (!guard('residence-permit:view')) return this.setData({ loading: false, error: '当前账号无权查看居住证信息' }); this._authorized = true; return this.load() },
  async load() { if (!this._authorized || this._loading) return; this._loading = true; this.setData({ loading: true, error: '' }); try { const permit = adapter.permit(await service.detail(this.data.id)); const user = getApp().globalData.user; this.setData({ permit, canApply: permit.rawStatus === 'ACTIVE' && can(user, 'residence-permit:apply'), canViewApplication: Boolean(permit.sourceApplicationId) && can(user, 'application:view') }) } catch (error) { this.setData({ error: messageOf(error) }) } finally { this._loading = false; this.setData({ loading: false }) } },
  apply() { wx.navigateTo({ url: `/pages/permits/endorsement/index?permitId=${this.data.id}` }) },
  openApplication() { if (this.data.canViewApplication) wx.navigateTo({ url: `/pages/applications/detail/index?id=${this.data.permit.sourceApplicationId}` }) }
})
