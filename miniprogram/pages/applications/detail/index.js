const service = require('../../../services/application')
const adapter = require('../../../adapters/application')
const { permissionsOf } = require('../../../utils/permission')
const { can } = require('../../../utils/permission')
const { messageOf } = require('../../../utils/error')
const { markForRefresh } = require('../../../utils/page-refresh')
Page({
  data: { id: null, application: null, materials: [], logs: [], progress: [], approvalResult: { available: false }, professionalFields: [], professionalMessage: '', canWithdraw: false, loading: true, error: '', openingMaterial: null, withdrawing: false },
  onLoad(options) { this.setData({ id: options.id }); this.load() },
  async load() {
    this.setData({ loading: true, error: '', professionalMessage: '' })
    try {
      const raw = await service.detail(this.data.id)
      const application = adapter.normalize(raw)
      const permissions = permissionsOf(getApp().globalData.user)
      const results = await Promise.allSettled([service.materials(this.data.id), service.logs(this.data.id), service.professional(this.data.id, raw.businessType, permissions)])
      const materials = results[0].status === 'fulfilled' ? (results[0].value || []).map(adapter.material) : []
      const logs = results[1].status === 'fulfilled' ? (results[1].value || []).map(adapter.log) : []
      let professionalMessage = ''
      if (results[2].status === 'rejected') professionalMessage = results[2].reason.statusCode === 404 ? '相关业务信息暂未登记，申请基本信息仍可查看。' : '相关业务信息暂时无法加载。'
      this.setData({ application, materials, logs, progress: adapter.progress(application, logs), approvalResult: adapter.approvalResult(logs), professionalFields: results[2].status === 'fulfilled' ? adapter.professionalFields(results[2].value) : [], professionalMessage, canWithdraw: application.canWithdraw && can(getApp().globalData.user, 'application:withdraw') })
    } catch (error) { this.setData({ application: null, error: messageOf(error) }) }
    finally { this.setData({ loading: false }) }
  },
  async openMaterial(e) {
    const id = e.currentTarget.dataset.id, contentType = e.currentTarget.dataset.type || ''
    this.setData({ openingMaterial: id })
    try { const path = await service.materialFile(id); if (contentType.startsWith('image/')) wx.previewImage({ urls: [path] }); else wx.openDocument({ filePath: path, showMenu: true, fail: () => wx.showToast({ title: '该文件类型暂不支持预览', icon: 'none' }) }) } catch (error) { wx.showToast({ title: messageOf(error), icon: 'none' }) } finally { this.setData({ openingMaterial: null }) }
  },
  async withdraw() {
    if (this.data.withdrawing || !this.data.canWithdraw) return
    const confirmed = await new Promise((resolve) => wx.showModal({ title: '撤回申请', content: '撤回后当前审批流程将结束，确定继续吗？', success: (result) => resolve(result.confirm) }))
    if (!confirmed) return
    this.setData({ withdrawing: true })
    try { await service.withdraw(this.data.id); markForRefresh('pages/applications/list/index'); wx.showToast({ title: '申请已撤回', icon: 'success' }); await this.load() }
    catch (error) { wx.showToast({ title: messageOf(error), icon: 'none' }); if (error.statusCode === 409) await this.load() }
    finally { this.setData({ withdrawing: false }) }
  }
})
