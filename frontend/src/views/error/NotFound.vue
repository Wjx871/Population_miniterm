<template>
  <div class="error-page">
    <div class="error-code">404</div>
    <h1 class="error-title">页面不存在</h1>
    <p class="error-desc">抱歉，您访问的页面不存在或已被移除。</p>
    <div class="error-actions">
      <el-button type="primary" @click="goHome">返回工作台</el-button>
      <el-button @click="goBack">返回上一页</el-button>
    </div>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'

const router = useRouter()

const goHome = () => {
  router.replace('/home')
}

const goBack = () => {
  router.back()
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
  font-size: 100px;
  font-weight: bold;
  color: var(--color-warning);
  line-height: 1;
  margin-bottom: 20px;
}
.error-title {
  font-size: 24px;
  color: var(--color-ink);
  margin-bottom: 16px;
}
.error-desc {
  font-size: 16px;
  color: var(--color-ink-muted);
  margin-bottom: 30px;
}
.error-actions {
  display: flex;
  gap: 16px;
}
</style>
