<template>
  <div class="home-container">
    <!-- 顶部导航栏 -->
    <header class="navbar">
      <div class="logo-area">
        <el-icon class="logo-icon"><Platform /></el-icon>
        <span class="system-title">人口数据库管理系统</span>
      </div>
      <div class="user-area" v-if="username">
        <el-avatar size="small" class="user-avatar">{{ username.charAt(0).toUpperCase() }}</el-avatar>
        <span class="username">{{ username }} ({{ role }})</span>
        <el-button type="danger" size="small" plain @click="handleLogout">退出登录</el-button>
      </div>
    </header>

    <!-- 主体内容区 -->
    <main class="main-content">
      <el-card class="welcome-card" shadow="hover">
        <template #header>
          <div class="card-header">
            <span>系统公告</span>
          </div>
        </template>
        <div class="welcome-text">
          <h1>欢迎使用人口数据库管理系统</h1>
          <p class="subtitle">Population Database Management System</p>
          <p class="desc">
            本系统致力于提供高效、安全的人口与户籍数据管理服务。包含人口管理、户口管理、迁入迁出管理、证件管理、流动人口管理、重点人口管理及数据大屏等功能。
          </p>
        </div>
        <div class="features-grid">
          <div class="feature-item">
            <el-icon class="feature-icon"><User /></el-icon>
            <h4>人口信息管理</h4>
          </div>
          <div class="feature-item">
            <el-icon class="feature-icon"><HomeFilled /></el-icon>
            <h4>户口管理</h4>
          </div>
          <div class="feature-item">
            <el-icon class="feature-icon"><Switch /></el-icon>
            <h4>迁入迁出管理</h4>
          </div>
          <div class="feature-item">
            <el-icon class="feature-icon"><Postcard /></el-icon>
            <h4>证件管理</h4>
          </div>
          <div class="feature-item">
            <el-icon class="feature-icon"><TrendCharts /></el-icon>
            <h4>数据统计分析</h4>
          </div>
        </div>
      </el-card>
    </main>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { Platform, User, HomeFilled, Switch, Postcard, TrendCharts } from '@element-plus/icons-vue';
import { ElMessageBox, ElMessage } from 'element-plus';

const router = useRouter();
const username = ref('');
const role = ref('');

onMounted(() => {
  username.value = localStorage.getItem('username') || '';
  role.value = localStorage.getItem('role') || '';
});

const handleLogout = () => {
  ElMessageBox.confirm('确定要退出登录吗？', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning',
  })
    .then(() => {
      // 清除本地存储的登录状态
      localStorage.removeItem('token');
      localStorage.removeItem('username');
      localStorage.removeItem('role');
      // 路由跳转
      router.push('/login');
      ElMessage.success('已退出登录');
    })
    .catch(() => {});
};
</script>

<style scoped>
.home-container {
  min-height: 100vh;
  background-color: #f5f7fa;
  display: flex;
  flex-direction: column;
}

.navbar {
  height: 60px;
  background-color: #fff;
  border-bottom: 1px solid #e6e6e6;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 20px;
  box-shadow: 0 1px 4px rgba(0, 21, 41, 0.08);
}

.logo-area {
  display: flex;
  align-items: center;
  gap: 10px;
}

.logo-icon {
  font-size: 24px;
  color: #409eff;
}

.system-title {
  font-size: 18px;
  font-weight: bold;
  color: #303133;
}

.user-area {
  display: flex;
  align-items: center;
  gap: 12px;
}

.user-avatar {
  background-color: #409eff;
}

.username {
  font-size: 14px;
  color: #606266;
  margin-right: 10px;
}

.main-content {
  flex: 1;
  padding: 40px 20px;
  display: flex;
  justify-content: center;
  align-items: flex-start;
}

.welcome-card {
  width: 100%;
  max-width: 800px;
  background-color: #fff;
}

.card-header {
  font-weight: bold;
  color: #303133;
}

.welcome-text {
  text-align: center;
  padding: 20px 0;
}

.welcome-text h1 {
  font-size: 28px;
  color: #303133;
  margin-bottom: 10px;
  font-weight: 600;
}

.welcome-text .subtitle {
  font-size: 16px;
  color: #909399;
  margin-bottom: 30px;
  letter-spacing: 1px;
}

.welcome-text .desc {
  font-size: 15px;
  color: #606266;
  line-height: 1.8;
  max-width: 600px;
  margin: 0 auto;
}

.features-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(120px, 1fr));
  gap: 20px;
  margin-top: 40px;
  padding: 20px;
  background-color: #f8fafc;
  border-radius: 8px;
}

.feature-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 15px;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  transition: all 0.3s;
}

.feature-item:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05);
  border-color: #409eff;
}

.feature-icon {
  font-size: 24px;
  color: #409eff;
  margin-bottom: 10px;
}

.feature-item h4 {
  font-size: 13px;
  color: #334155;
  margin: 0;
  font-weight: 500;
}
</style>
