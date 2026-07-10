import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '../router'
import { useUserStore } from '../stores/user'

const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 10000,
  headers: { 'Content-Type': 'application/json;charset=UTF-8' },
})

request.interceptors.request.use(
  (config) => {
    const userStore = useUserStore()
    if (userStore.accessToken) {
      config.headers.Authorization = `${userStore.tokenType || 'Bearer'} ${userStore.accessToken}`
    }
    return config
  },
  (error) => Promise.reject(error)
)

request.interceptors.response.use(
  (response) => {
    const result = response.data
    if (!result || typeof result.code === 'undefined') return result
    if (result.code >= 200 && result.code < 300) return result.data
    return rejectBusinessResponse(result)
  },
  (error) => {
    const status = error.response?.status
    if (status === 401) {
      clearAndRedirectToLogin()
    } else if (status === 403) {
      ElMessage.error('当前账号无权执行该操作')
    } else if (status === 404) {
      ElMessage.error(`请求的接口不存在 (404): ${error.config?.url}`)
    } else {
      ElMessage.error(error.response?.data?.message || '网络异常，请检查后端服务是否启动')
    }
    return Promise.reject(error)
  }
)

function rejectBusinessResponse(result) {
  if (result.code === 401) {
    clearAndRedirectToLogin()
  } else if (result.code === 403) {
    ElMessage.error('当前账号无权执行该操作')
  } else {
    ElMessage.error(result.message || '操作失败')
  }
  return Promise.reject(new Error(result.message || '请求失败'))
}

function clearAndRedirectToLogin() {
  const userStore = useUserStore()
  userStore.clearAuth()
  ElMessage.error('登录状态已失效，请重新登录')
  if (router.currentRoute.value.path !== '/login') router.replace('/login')
}

export default request
