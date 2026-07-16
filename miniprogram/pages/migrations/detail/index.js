const migrationService = require('../../../services/migration')
const applicationService = require('../../../services/application')
const migrationAdapter = require('../../../adapters/mobile-business')
const applicationAdapter = require('../../../adapters/application')
const { guard } = require('../../../utils/permission')
const { messageOf } = require('../../../utils/error')
Page({
  data: { applicationNo: '', record: null, application: null, loading: true, error: '' },
  onLoad(options) { this.setData({ applicationNo: decodeURIComponent(options.applicationNo || '') }); if (!guard('migration:view')) return this.setData({ loading: false, error: '当前账号无权查看迁移记录' }); this._authorized = true; return this.load() },
  async load() { if (!this._authorized || this._loading) return; this._loading = true; this.setData({ loading: true, error: '' }); try { const no = this.data.applicationNo; const [migrationPage, applicationPage] = await Promise.all([migrationService.list({ applicationNo: no, page: 0, size: 10 }), applicationService.list({ applicationNo: no, page: 0, size: 10 })]); const record = ((migrationPage && migrationPage.content) || []).find((item) => item.applicationNo === no); const application = ((applicationPage && applicationPage.content) || []).find((item) => item.applicationNo === no); if (!record) throw { statusCode: 404, message: '未找到对应迁移记录' }; this.setData({ record: migrationAdapter.migration(record), application: application ? applicationAdapter.normalize(application) : null }) } catch (error) { this.setData({ error: messageOf(error) }) } finally { this._loading = false; this.setData({ loading: false }) } },
  openApplication() { if (this.data.application && this.data.application.applicationId) wx.navigateTo({ url: `/pages/applications/detail/index?id=${this.data.application.applicationId}` }) }
})
