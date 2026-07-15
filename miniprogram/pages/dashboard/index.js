const dashboard = require('../../services/dashboard')
const { can } = require('../../utils/permission')
const { normalizeUser } = require('../../adapters/auth')
const { normalizeMetrics, normalizeHealth, dashboardEntries } = require('../../adapters/dashboard')
const { resolveErrorState } = require('../../utils/error-state')
const { messageOf } = require('../../utils/error')
const { syncTabBar } = require('../../utils/tabBar.js')

Page({
  data: {
    user: {},
    entries: [],
    metrics: normalizeMetrics(null, null),
    health: normalizeHealth(null),
    loading: false,
    metricsError: '',
    metricsErrorType: 'unknown',
    metricNotice: ''
  },

  onShow() {
    syncTabBar(this, 'dashboard')
    const user = normalizeUser(getApp().globalData.user)
    if (!user.userId) return
    this.setData({ user, entries: dashboardEntries(user) })
    return this.load()
  },

  async load() {
    if (this.data.loading) return
    const user = this.data.user
    const canViewStatistics = can(user, 'statistics:view')
    const canViewHouseholds = can(user, 'household:view')
    this.setData({ loading: true, metricsError: '', metricNotice: '' })

    const overviewJob = canViewStatistics ? dashboard.overview() : Promise.resolve(null)
    const householdJob = canViewHouseholds ? dashboard.householdTotal() : Promise.resolve(null)
    const [overviewResult, householdResult, healthResult] = await Promise.allSettled([
      overviewJob,
      householdJob,
      dashboard.health()
    ])

    const metricResults = [
      canViewStatistics ? overviewResult : null,
      canViewHouseholds ? householdResult : null
    ].filter(Boolean)
    const metricFailures = metricResults.filter((result) => result.status === 'rejected')
    const allMetricsFailed = metricResults.length > 0 && metricFailures.length === metricResults.length
    const firstMetricError = metricFailures[0] && metricFailures[0].reason
    let metricNotice = ''
    if (!metricResults.length) metricNotice = '当前账号暂无指标查看权限'
    else if (metricFailures.length && !allMetricsFailed) metricNotice = '部分指标暂不可用，请稍后刷新'

    this.setData({
      metrics: normalizeMetrics(
        overviewResult.status === 'fulfilled' ? overviewResult.value : null,
        householdResult.status === 'fulfilled' ? householdResult.value : null
      ),
      health: normalizeHealth(healthResult.status === 'fulfilled' ? healthResult.value : null),
      metricsError: allMetricsFailed ? '核心指标暂不可用，请稍后重试' : '',
      metricsErrorType: allMetricsFailed ? resolveErrorState({
        statusCode: firstMetricError && firstMetricError.statusCode,
        message: messageOf(firstMetricError)
      }).type : 'unknown',
      metricNotice
    })
    this.setData({ loading: false })
    wx.stopPullDownRefresh()
  },

  onPullDownRefresh() {
    if (this.data.loading) {
      wx.stopPullDownRefresh()
      return
    }
    return this.load()
  },

  open(event) {
    const url = event.currentTarget.dataset.url
    if (url) wx.navigateTo({ url })
  }
})
