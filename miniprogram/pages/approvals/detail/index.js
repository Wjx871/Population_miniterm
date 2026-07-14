const approvalService = require('../../../services/approval')
const applicationService = require('../../../services/application')
const adapter = require('../../../adapters/application')
const { can, permissionsOf } = require('../../../utils/permission')
const { messageOf } = require('../../../utils/error')
Page({
  data: { id: null, detail: null, application: null, materials: [], logs: [], professionalFields: [], professionalMessage: '', canHandle: false, comment: '', loading: true, error: '', submitting: false },
  onLoad(options) { this.setData({ id: options.id, canHandle: can(getApp().globalData.user, 'approval:handle') }); this.load() },
  inputComment(e) { this.setData({ comment: e.detail.value }) },
  async load() {
    this.setData({ loading: true, error: '', professionalMessage: '' })
    try {
      const raw = await approvalService.detail(this.data.id)
      let professional = null, professionalMessage = ''
      try { professional = await applicationService.professional(raw.application.applicationId, raw.application.businessType, permissionsOf(getApp().globalData.user)) }
      catch (error) { professionalMessage = error.statusCode === 404 ? '专业业务记录不存在，审批基础信息仍可查看。' : '专业业务摘要暂不可查看。' }
      this.setData({ detail: raw.approval, application: adapter.normalize(raw.application), materials: (raw.materials || []).map(adapter.material), logs: (raw.logs || []).map(adapter.log), professionalFields: adapter.professionalFields(professional), professionalMessage, comment: '' })
    } catch (error) { this.setData({ detail: null, error: messageOf(error) }) }
    finally { this.setData({ loading: false }) }
  },
  async decide(e) {
    const action = e.currentTarget.dataset.action
    if (!this.data.canHandle || !this.data.detail || this.data.detail.status !== 'PENDING') return
    if (action === 'reject' && !this.data.comment.trim()) { wx.showToast({ title: '请填写驳回原因', icon: 'none' }); return }
    const label = action === 'approve' ? '通过' : '驳回'
    const confirmed = await new Promise((resolve) => wx.showModal({ title: `确认${label}`, content: action === 'approve' ? '通过后仍需专业人员显式执行业务，确定继续吗？' : '确定驳回该申请吗？', success: (r) => resolve(r.confirm) }))
    if (!confirmed) return
    this.setData({ submitting: true })
    try { await approvalService[action](this.data.id, this.data.detail.version, this.data.comment.trim()); wx.showToast({ title: action === 'approve' ? '审批已通过，业务尚未执行' : '申请已驳回', icon: 'none', duration: 2500 }); await this.load() } catch (error) { wx.showToast({ title: messageOf(error), icon: 'none', duration: 2600 }); if (error.statusCode === 409) await this.load() } finally { this.setData({ submitting: false }) }
  },
  async openMaterial(e) { try { const path = await applicationService.materialFile(e.currentTarget.dataset.id); const type = e.currentTarget.dataset.type || ''; if (type.startsWith('image/')) wx.previewImage({ urls: [path] }); else wx.openDocument({ filePath: path, showMenu: true }) } catch (error) { wx.showToast({ title: messageOf(error), icon: 'none' }) } }
})
