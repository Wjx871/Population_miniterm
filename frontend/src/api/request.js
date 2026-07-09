import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '../router'
import { useUserStore } from '../stores/user'

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
    return config
  },
  (error) => Promise.reject(error)
)

request.interceptors.response.use(
  (response) => {
    const result = response.data

    if (!result || typeof result.success === 'undefined') {
      return result
    }

    if (result.success) {
      return result.data
    }

    // 后端如果直接在返回体中标记失败
    ElMessage.error(result.message || '操作失败')
    return Promise.reject(result)
  },
  (error) => {
    if (error.response?.status === 401) {
      const userStore = useUserStore()
      userStore.logout()
      ElMessage.error('登录状态已失效，请重新登录')
      router.replace('/login')
    } else if (error.response?.status === 404) {
      // 临时隐藏 404 报错，避免因后端接口未实现而频繁弹窗影响体验
      console.warn(`请求的接口不存在 (404): ${error.config?.url}`);
    } else {
      ElMessage.error(error.response?.data?.message || '网络异常，请检查后端服务是否启动')
    }
    return Promise.reject(error)
  }
)

export default request
