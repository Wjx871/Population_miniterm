import { ref, reactive, onMounted, onUnmounted } from 'vue'
import { getDashboardOverview, getDashboardCharts } from '../../../api/dashboard'
import { normalizeDashboardOverview, normalizeDashboardCharts } from '../../../adapters/dashboard'
import { demoOverview, demoCharts } from '../demo/dashboardDemoFixture'

export function useDashboardData() {
  const overviewLoading = ref(false)
  const chartsLoading = ref(false)
  const overviewError = ref(false)
  const chartsError = ref(false)
  
  const overview = reactive({})
  const charts = reactive({})
  
  // 检查是否开启演示模式（默认开启，除非显式设为 'false'，方便在不重启Vite时直接生效）
  const isDemo = ref(import.meta.env.VITE_DASHBOARD_DEMO !== 'false')

  let timer = null

  const loadOverview = async () => {
    if (overviewLoading.value) return
    overviewLoading.value = true
    try {
      let data
      if (isDemo.value) {
        data = demoOverview
        // Simulate network delay
        await new Promise(r => setTimeout(r, 500))
      } else {
        data = await getDashboardOverview()
      }
      
      const normalized = normalizeDashboardOverview(data)
      // 如果真实模式缺失某些必须的字段，不要覆盖已经成功的字段，仅为了防止 undefined 报错
      // 但是在真实的 adapter 里其实已经处理了基础字段。
      Object.keys(normalized).forEach(key => {
        overview[key] = normalized[key]
      })
      
      // 因为 normalizeAdapter 严格遵循后端真实的 DTO 会剔除前端 mock 的数据，
      // 所以对于演示模式专属的数据，我们需要手动注入。
      
      overviewError.value = false
    } catch (e) {
      console.error('Failed to load dashboard overview', e)
      Object.assign(overview, normalizeDashboardOverview())
      overviewError.value = true
    } finally {
      overviewLoading.value = false
    }
  }

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
      Object.assign(charts, normalizeDashboardCharts())
      chartsError.value = true
    } finally {
      chartsLoading.value = false
    }
  }

  const loadAll = async () => {
    // 只有全成功时，外界才知道整体加载完成
    await Promise.allSettled([loadOverview(), loadCharts()])
  }

  const toggleDemoMode = (val) => {
    isDemo.value = val
    loadAll()
  }

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

  const onVisibilityChange = () => {
    if (!document.hidden && !overviewLoading.value && !chartsLoading.value) {
      loadAll()
    }
  }

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
