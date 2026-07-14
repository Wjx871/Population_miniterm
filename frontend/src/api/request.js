import axios from 'axios'
import { ElMessage } from 'element-plus'
import { useUserStore } from '../stores/user.js'

let handlingUnauthorized = false

async function handleUnauthorized(message) {
  const userStore = useUserStore()
  userStore.clearSession()
  if (handlingUnauthorized) return
  handlingUnauthorized = true
  try {
    ElMessage.error(message || '登录状态已失效，请重新登录')
    const { default: router } = await import('../router/index.js')
    if (router.currentRoute.value.path !== '/login') {
      await router.replace({ path: '/login', query: { redirect: router.currentRoute.value.fullPath } })
    }
  } finally {
    handlingUnauthorized = false
  }
}

const request = axios.create({
  baseURL: (typeof import.meta !== 'undefined' && import.meta.env && import.meta.env.VITE_API_BASE_URL) || '/api',
  timeout: 10000,
  headers: { 'Content-Type': 'application/json;charset=UTF-8' },
})

function handleForbidden(message, config) {
  if (!config?.silent) {
    ElMessage.error(message || '无权执行该操作')
  }
  if (config?.redirectOn403) {
    import('../router/index.js').then((m) => {
      const router = m.default
      if (router.currentRoute.value.path !== '/403') {
        router.replace({ path: '/403', query: { from: router.currentRoute.value.fullPath } })
      }
    })
  }
}

request.interceptors.request.use((config) => {
  const userStore = useUserStore()
  if (userStore.accessToken) {
    config.headers.Authorization = `${userStore.tokenType || 'Bearer'} ${userStore.accessToken}`
  }
  if (config.data instanceof FormData) delete config.headers['Content-Type']
  return config
}, (error) => Promise.reject(error))

request.interceptors.response.use((response) => {
  if (response.config.rawResponse) return response
  const result = response.data
  if (!result || typeof result.code === 'undefined') return result
  if (result.code >= 200 && result.code < 300) return result.data
  if (result.code === 401) {
    void handleUnauthorized(result.message)
    return Promise.reject(new Error(result.message || '未授权'))
  }
  if (result.code === 403) {
    handleForbidden(result.message, response.config)
    return Promise.reject(new Error(result.message || '无权执行该操作'))
  }
  ElMessage.error(result.message || '操作失败')
  return Promise.reject(result)
}, (error) => {
  const status = error.response?.status
  const message = error.response?.data?.message
  if (status === 409 || error.response?.data?.code === 409) return Promise.reject(error)
  if (status === 401) {
    void handleUnauthorized(message)
  } else if (status === 403) {
    handleForbidden(message || '无权执行该操作 (403)', error.config)
  } else if (status === 410) {
    // 由下载等业务调用方解释资源已失效。
  } else if (status === 404) {
    ElMessage.error(message || '请求的资源不存在或无权查看')
  } else if (error.response) {
    ElMessage.error(message || '请求失败')
  } else {
    ElMessage.error(message || '网络异常，请检查后端服务是否启动')
  }
  return Promise.reject(error)
})

export default request
