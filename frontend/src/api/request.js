import axios from 'axios'
import { ElMessage } from 'element-plus'
import { useUserStore } from '../stores/user'

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
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 10000,
  headers: {
    'Content-Type': 'application/json;charset=UTF-8',
  },
})

request.interceptors.request.use(
  (config) => {
    const userStore = useUserStore()
    if (userStore.accessToken) {
      config.headers.Authorization = `${userStore.tokenType || 'Bearer'} ${userStore.accessToken}`
    }
    if (config.data instanceof FormData) {
      delete config.headers['Content-Type']
    }
    return config
  },
  (error) => Promise.reject(error)
)

request.interceptors.response.use(
  (response) => {
    if (response.config.rawResponse) {
      return response
    }
    const result = response.data

    if (!result || typeof result.code === 'undefined') {
      return result
    }

    if (result.code >= 200 && result.code < 300) {
      return result.data
    }

    if (result.code === 401) {
      void handleUnauthorized(result.message)
      return Promise.reject(new Error(result.message || '未授权'))
    }

    if (result.code === 403) {
      ElMessage.error(result.message || '无权执行该操作')
      import('../router/index.js').then(({ default: router }) => {
        if (router.currentRoute.value.path !== '/403') router.replace({ path: '/403', query: { from: router.currentRoute.value.fullPath } })
      })
      return Promise.reject(new Error(result.message || '无权执行该操作'))
    }

    // 其他业务错误
    ElMessage.error(result.message || '操作失败')
    return Promise.reject(result)
  },
  (error) => {
    if (error.response?.status === 409 || error.response?.data?.code === 409) {
      return Promise.reject(error)
    }
    if (error.response?.status === 401) {
      void handleUnauthorized(error.response?.data?.message)
    } else if (error.response?.status === 403) {
      ElMessage.error(error.response?.data?.message || '无权执行该操作 (403)')
      import('../router/index.js').then(({ default: router }) => {
        if (router.currentRoute.value.path !== '/403') router.replace({ path: '/403', query: { from: router.currentRoute.value.fullPath } })
      })
    } else if (error.response?.status === 404) {
      ElMessage.error(`请求的接口不存在 (404): ${error.config?.url}`)
    } else {
      ElMessage.error(error.response?.data?.message || '网络异常，请检查后端服务是否启动')
    }
    return Promise.reject(error)
  }
)

export default request
