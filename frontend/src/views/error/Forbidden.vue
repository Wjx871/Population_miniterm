<template>
  <div class="error-page">
    <div class="error-code">403</div>
    <h1 class="error-title">无权访问</h1>
    <p class="error-desc">抱歉，您当前的权限不足以访问此页面或执行该操作。</p>
    <div class="error-actions">
      <el-button type="primary" @click="goHome">返回工作台</el-button>
      <el-button @click="goBack">返回上一页</el-button>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'

const router = useRouter()

const goHome = () => {
  router.replace('/home')
}

const goBack = () => {
  if (window.history.length > 1) {
    router.back()
  } else {
    router.replace('/home')
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
  font-size: 100px;
  font-weight: bold;
  color: var(--color-danger);
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
