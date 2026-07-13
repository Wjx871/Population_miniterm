import axios from 'axios'
import { ElMessage } from 'element-plus'
import { useUserStore } from '../stores/user.js'

const request = axios.create({
  baseURL: (typeof import.meta !== 'undefined' && import.meta.env && import.meta.env.VITE_API_BASE_URL) || '/api',
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

/**
 * 401：清理登录态并跳转登录。
 * 403：默认只提示，不强制跳转整页 403。
 *     页面路由权限由路由守卫负责；接口级 403 由业务页自行展示。
 *     仅当请求显式设置 config.redirectOn403 = true 时才跳转 /403。
 */
function handleUnauthorized() {
  const userStore = useUserStore()
  userStore.logout()
  ElMessage.error('登录状态已失效，请重新登录')
  import('../router/index.js').then((m) => m.default.replace('/login'))
}

function handleForbidden(message, config) {
  ElMessage.error(message || '无权执行该操作')
  if (config?.redirectOn403) {
    import('../router/index.js').then((m) => {
      const router = m.default
      if (router.currentRoute.value.path !== '/403') {
        router.replace({ path: '/403', query: { from: router.currentRoute.value.fullPath } })
      }
    })
  }
}

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
      handleUnauthorized()
      return Promise.reject(new Error(result.message || '未授权'))
    }

    if (result.code === 403) {
      handleForbidden(result.message, response.config)
      return Promise.reject(new Error(result.message || '无权执行该操作'))
    }

    ElMessage.error(result.message || '操作失败')
    return Promise.reject(result)
  },
  (error) => {
    if (error.response?.status === 409 || error.response?.data?.code === 409) {
      return Promise.reject(error)
    }
    if (error.response?.status === 401) {
      handleUnauthorized()
    } else if (error.response?.status === 403) {
      handleForbidden(error.response?.data?.message || '无权执行该操作 (403)', error.config)
    } else if (error.response?.status === 410) {
      // 410 由业务层（如下载）解释，不在此统一跳转
    } else if (error.response?.status === 404) {
      ElMessage.error(`请求的接口不存在 (404): ${error.config?.url}`)
    } else if (error.response) {
      ElMessage.error(error.response?.data?.message || '请求失败')
    } else {
      ElMessage.error('网络异常，请检查后端服务是否启动')
    }
    return Promise.reject(error)
  }
)

export default request
