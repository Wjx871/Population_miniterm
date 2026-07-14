const dashboard = require('../../services/dashboard')
const approval = require('../../services/approval')
const { can } = require('../../utils/permission')
const { normalizeUser } = require('../../adapters/auth')
const { messageOf } = require('../../utils/error')

function entries(user) {
  return [
    { title: '人口查询', desc: '查询人口基础档案', permission: 'population:view', url: '/pages/persons/list/index', icon: '人' },
    { title: '家庭户查询', desc: '查询家庭户及成员', permission: 'household:view', url: '/pages/households/list/index', icon: '户' },
    { title: '我的申请', desc: '申请状态和审批轨迹', permission: 'application:view', url: '/pages/applications/list/index', icon: '申' },
    { title: '移动审批', desc: '待审批与已审批事项', permission: 'approval:view', url: '/pages/approvals/list/index', icon: '审' },
    { title: '个人中心', desc: '账号、权限和服务状态', permission: null, url: '/pages/profile/index', icon: '我' }
  ].filter((item) => !item.permission || can(user, item.permission))
}

Page({
  data: { user: {}, entries: [], loading: true, error: '', stats: null },
  onShow() {
    const user = normalizeUser(getApp().globalData.user)
    if (!user.userId) return
    this.setData({ user, entries: entries(user) })
    this.load()
  },
  async load() {
    const user = this.data.user
    this.setData({ loading: true, error: '' })
    try {
      const jobs = []
      if (can(user, 'statistics:view')) jobs.push(dashboard.overview())
      else jobs.push(Promise.resolve({ registeredPopulation: null, pendingApprovals: null }))
      if (can(user, 'household:view')) jobs.push(dashboard.householdTotal())
      else jobs.push(Promise.resolve(null))
      const [overview, householdCount] = await Promise.all(jobs)
      this.setData({ stats: { population: overview.registeredPopulation, households: householdCount, pending: overview.pendingApprovals } })
    } catch (error) { this.setData({ error: messageOf(error), stats: null }) }
    finally { this.setData({ loading: false }); wx.stopPullDownRefresh() }
  },
  onPullDownRefresh() { this.load() },
  open(e) { wx.navigateTo({ url: e.currentTarget.dataset.url }) }
})
