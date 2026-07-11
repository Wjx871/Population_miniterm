import { useUserStore } from '../stores/user'

/**
 * v-permission 权限指令
 * 只有当用户拥有对应权限时，元素才会显示，否则会将其从 DOM 中移除
 * 
 * 用法：
 * <el-button v-permission="'person:create'">新增人口</el-button>
 * <el-button v-permission="['person:update', 'person:delete']">操作</el-button>
 */
export default {
  mounted(el, binding) {
    const { value } = binding
    const userStore = useUserStore()

    if (value) {
      const hasPermission = userStore.hasAnyPermission(value)

      if (!hasPermission) {
        // 如果没有权限，则移除该 DOM 元素
        el.parentNode && el.parentNode.removeChild(el)
      }
    } else {
      // 传入的权限值无效，默认当做无权限处理
      el.parentNode && el.parentNode.removeChild(el)
    }
  }
}
