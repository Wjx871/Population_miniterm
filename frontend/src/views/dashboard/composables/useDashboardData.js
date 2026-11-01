/**
 * 【数据大屏链路 · 前端数据调度核心】useDashboardData
 * 上游：DataDashboard.vue 在 setup 中调用本 composable
 * 下游：api/dashboard（真实模式）或 demoFixture（演示模式）→ adapters/dashboard 规范化 → 写入 overview/charts
 * 答辩要点：页面不直接请求；本文件负责何时 load、调哪个 API、失败如何兜底、每 5 分钟自动刷新
 */
import { ref, reactive, onMounted, onUnmounted } from 'vue'
import { getDashboardOverview, getDashboardCharts } from '../../../api/dashboard'
import { normalizeDashboardOverview, normalizeDashboardCharts } from '../../../adapters/dashboard'
import { demoOverview, demoCharts } from '../demo/dashboardDemoFixture'

export function useDashboardData() {
  const overviewLoading = ref(false)
  const chartsLoading = ref(false)
  const overviewError = ref(false)
  const chartsError = ref(false)

  // Vue 响应式：normalize 后写入，模板/子组件自动更新
  const overview = reactive({})
  const charts = reactive({})

  // 演示模式默认开启（VITE_DASHBOARD_DEMO !== 'false'），答辩可无后端也能展示
  const isDemo = ref(import.meta.env.VITE_DASHBOARD_DEMO !== 'false')

  let timer = null

  // === 加载顶部 KPI + 人口结构 + 重点业务（GET /dashboard/overview）===
  const loadOverview = async () => {
    if (overviewLoading.value) return
    overviewLoading.value = true
    try {
      let data
      if (isDemo.value) {
        data = demoOverview
        // 模拟网络延迟，便于观察 loading 态
        await new Promise(r => setTimeout(r, 500))
      } else {
        // 真实模式：经 request 带 JWT，响应拦截器已解包 data
        data = await getDashboardOverview()
      }

      const normalized = normalizeDashboardOverview(data)
      // 如果真实模式缺失某些必须的字段，不要覆盖已经成功的字段，仅为了防止 undefined 报错
      // 但是在真实的 adapter 里其实已经处理了基础字段。
      Object.keys(normalized).forEach(key => {
        // 保留已有非空值，避免 catch 后又用空对象覆盖 demo 数据
        if (overview[key] != null && (Array.isArray(overview[key]) ? overview[key].length > 0 : true)) {
          // 仅当新值"更具体"时才覆盖（粗略启发式）
          if (typeof normalized[key] === 'number' && overview[key] === 0) {
            overview[key] = normalized[key]
          } else if (Array.isArray(normalized[key]) && normalized[key].length > 0) {
            overview[key] = normalized[key]
          } else if (!Array.isArray(normalized[key]) && normalized[key] !== null && normalized[key] !== undefined && typeof normalized[key] !== 'object') {
            overview[key] = normalized[key]
          } else if (typeof normalized[key] === 'object' && normalized[key] !== null) {
            overview[key] = normalized[key]
          }
          // 其他情况保留原值
        } else {
          overview[key] = normalized[key]
        }
      })

      // 因为 normalizeAdapter 严格遵循后端真实的 DTO 会剔除前端 mock 的数据，
      // 所以对于演示模式专属的数据，我们需要手动注入。

      overviewError.value = false
    } catch (e) {
      console.error('Failed to load dashboard overview', e)
      // 失败时仅补默认值，但不会再次覆盖已有非空数据，避免把已渲染的数据抹掉
      const fallback = normalizeDashboardOverview()
      Object.keys(fallback).forEach(key => {
        const current = overview[key]
        const empty = current == null || (Array.isArray(current) && current.length === 0)
        if (empty) {
          overview[key] = fallback[key]
        }
      })
      overviewError.value = true
    } finally {
      overviewLoading.value = false
    }
  }

  // === 加载趋势/分布图数据（GET /dashboard/charts?days=30&regionLimit=8）===
  const loadCharts = async () => {
    if (chartsLoading.value) return
    chartsLoading.value = true
    try {
      let data
      if (isDemo.value) {
        data = demoCharts
        await new Promise(r => setTimeout(r, 600))
      } else {
        data = await getDashboardCharts({ days: 30, regionLimit: 8 })
      }

      const normalized = normalizeDashboardCharts(data)
      Object.keys(normalized).forEach(key => {
        charts[key] = normalized[key]
      })

      // 注入被 adapter 剔除的演示数据

      chartsError.value = false
    } catch (e) {
      console.error('Failed to load dashboard charts', e)
      const fallback = normalizeDashboardCharts()
      Object.keys(fallback).forEach(key => {
        const current = charts[key]
        const empty = current == null || (Array.isArray(current) && current.length === 0)
        if (empty) {
          charts[key] = fallback[key]
        }
      })
      chartsError.value = true
    } finally {
      chartsLoading.value = false
    }
  }

  // 并行拉 overview + charts；allSettled 保证一侧失败不拖死另一侧
  const loadAll = async () => {
    // 只有全成功时，外界才知道整体加载完成
    await Promise.allSettled([loadOverview(), loadCharts()])
  }

  // 头部切换演示/真实后立即重新拉数
  const toggleDemoMode = (val) => {
    isDemo.value = val
    loadAll()
  }

  // 每 5 分钟自动刷新；页签隐藏时不刷，避免浪费请求
  const startAutoRefresh = () => {
    stopAutoRefresh()
    timer = setInterval(() => {
      if (!document.hidden && !overviewLoading.value && !chartsLoading.value) {
        loadAll()
      }
    }, 300000) // 5 minutes
  }

  const stopAutoRefresh = () => {
    if (timer) clearInterval(timer)
  }

  // 从后台切回前台时补一次刷新
  const onVisibilityChange = () => {
    if (!document.hidden && !overviewLoading.value && !chartsLoading.value) {
      loadAll()
    }
  }

  // 页面挂载：首屏 loadAll + 定时刷新 + 可见性监听
  onMounted(() => {
    loadAll()
    startAutoRefresh()
    document.addEventListener('visibilitychange', onVisibilityChange)
  })

  onUnmounted(() => {
    stopAutoRefresh()
    document.removeEventListener('visibilitychange', onVisibilityChange)
  })

  return {
    overview,
    charts,
    overviewLoading,
    chartsLoading,
    overviewError,
    chartsError,
    isDemo,
    loadOverview,
    loadCharts,
    loadAll,
    toggleDemoMode
  }
}
