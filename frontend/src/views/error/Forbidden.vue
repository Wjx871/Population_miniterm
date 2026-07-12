<template>
  <div class="error-page">
    <div class="error-code">403</div>
    <h1 class="error-title">无权访问</h1>
    <p class="error-desc">抱歉，您当前的权限不足以访问此页面或执行该操作。</p>
    <div class="error-actions">
      <el-button type="primary" @click="goHome">返回可访问页面</el-button>
      <el-button @click="goBack">返回上一页</el-button>
    </div>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { useUserStore } from '../../stores/user'
import { resolveLandingPath } from '../../utils/routeAccess'

const router = useRouter()
const userStore = useUserStore()

const goHome = () => {
  const landing = resolveLandingPath(
    userStore.permissions,
    userStore.permissionLevel,
    router.getRoutes()
  )
  router.replace(landing)
}

const goBack = () => {
  if (window.history.length > 1) {
    router.back()
  } else {
    goHome()
  }
}
</script>

<style scoped>
.error-page {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  padding: 40px;
  text-align: center;
}
.error-code {
  font-size: 84px;
  font-weight: 800;
  color: #94a3b8;
  line-height: 1;
}
.error-title {
  margin: 12px 0 8px;
  font-size: 24px;
}
.error-desc {
  color: #64748b;
  margin-bottom: 20px;
}
.error-actions {
  display: flex;
  gap: 12px;
}
</style>
