<template>
  <div class="home-container">
    <!-- 顶部导航栏: 严谨的深蓝政务顶栏 -->
    <header class="navbar">
      <div class="logo-area">
        <el-icon class="logo-icon"><Platform /></el-icon>
        <span class="system-title">人口数据库管理系统</span>
      </div>
      <div class="user-area" v-if="username">
        <span class="username">{{ username }} <span class="role-badge">{{ role }}</span></span>
        <el-button class="logout-btn" text @click="handleLogout">退出</el-button>
      </div>
    </header>

    <div class="main-layout">
      <!-- 侧边栏: 可扩展的模块导航 -->
      <aside class="sidebar">
        <nav class="side-nav">
          <a href="#" class="nav-item active">
            <el-icon><DataBoard /></el-icon> 工作台
          </a>
          <a href="#" class="nav-item">
            <el-icon><User /></el-icon> 人口管理
          </a>
          <a href="#" class="nav-item">
            <el-icon><HomeFilled /></el-icon> 户籍系统
          </a>
          <a href="#" class="nav-item">
            <el-icon><Setting /></el-icon> 系统设置
          </a>
        </nav>
      </aside>

      <!-- 主体内容区 -->
      <main class="main-content">
        <div class="page-header">
          <h1>工作台</h1>
          <p class="subtitle">欢迎使用人口数据库管理系统，今日系统运行状态良好。</p>
        </div>

        <!-- 核心业务网格 -->
        <div class="features-grid">
          <div class="feature-card">
            <div class="card-icon"><el-icon><User /></el-icon></div>
            <div class="card-info">
              <h3>人口信息管理</h3>
              <p>人口基础信息录入与维护</p>
            </div>
            <el-icon class="arrow"><ArrowRight /></el-icon>
          </div>
          
          <div class="feature-card">
            <div class="card-icon"><el-icon><HomeFilled /></el-icon></div>
            <div class="card-info">
              <h3>户口管理</h3>
              <p>家庭户籍档案与变更登记</p>
            </div>
            <el-icon class="arrow"><ArrowRight /></el-icon>
          </div>
          
          <div class="feature-card">
            <div class="card-icon"><el-icon><Switch /></el-icon></div>
            <div class="card-info">
              <h3>迁入迁出管理</h3>
              <p>户口迁移流转审批与记录</p>
            </div>
            <el-icon class="arrow"><ArrowRight /></el-icon>
          </div>
          
          <div class="feature-card">
            <div class="card-icon"><el-icon><Postcard /></el-icon></div>
            <div class="card-info">
              <h3>证件管理</h3>
              <p>身份证明及相关证件挂失补办</p>
            </div>
            <el-icon class="arrow"><ArrowRight /></el-icon>
          </div>
          
          <div class="feature-card">
            <div class="card-icon"><el-icon><TrendCharts /></el-icon></div>
            <div class="card-info">
              <h3>数据统计分析</h3>
              <p>人口结构宏观统计与图表展示</p>
            </div>
            <el-icon class="arrow"><ArrowRight /></el-icon>
          </div>
        </div>
      </main>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { 
  Platform, User, HomeFilled, Switch, Postcard, TrendCharts, DataBoard, Setting, ArrowRight 
} from '@element-plus/icons-vue';
import { ElMessageBox, ElMessage } from 'element-plus';

const router = useRouter();
const username = ref('');
const role = ref('');

onMounted(() => {
  username.value = localStorage.getItem('username') || '';
  role.value = localStorage.getItem('role') || '';
});

const handleLogout = () => {
  ElMessageBox.confirm('确定要退出登录吗？', '系统提示', {
    confirmButtonText: '确定退出',
    cancelButtonText: '取消',
    type: 'warning',
  })
    .then(() => {
      localStorage.removeItem('token');
      localStorage.removeItem('username');
      localStorage.removeItem('role');
      router.push('/login');
      ElMessage.success('已安全退出系统');
    })
    .catch(() => {});
};
</script>

<style scoped>
.home-container {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background-color: var(--color-surface-muted);
  overflow: hidden;
}

/* 顶部严谨深蓝导航 */
.navbar {
  height: 56px;
  background-color: var(--color-accent);
  color: #ffffff;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 24px;
  box-shadow: var(--shadow-subtle);
  z-index: 10;
}

.logo-area {
  display: flex;
  align-items: center;
  gap: 12px;
}

.logo-icon {
  font-size: 20px;
  color: #ffffff;
}

.system-title {
  font-size: 16px;
  font-weight: 600;
  letter-spacing: 1px;
}

.user-area {
  display: flex;
  align-items: center;
  gap: 16px;
  font-size: 14px;
}

.role-badge {
  background: rgba(255, 255, 255, 0.2);
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 12px;
  margin-left: 8px;
}

.logout-btn {
  color: rgba(255, 255, 255, 0.8) !important;
}
.logout-btn:hover {
  color: #ffffff !important;
  background: rgba(255, 255, 255, 0.1) !important;
}

/* 主体骨架 */
.main-layout {
  display: flex;
  flex: 1;
  overflow: hidden;
}

/* 侧边栏 */
.sidebar {
  width: 240px;
  background: var(--color-surface);
  border-right: 1px solid var(--color-border);
  padding: 16px 0;
  display: flex;
  flex-direction: column;
}
.side-nav {
  display: flex;
  flex-direction: column;
}
.nav-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 24px;
  color: var(--color-ink);
  font-size: 14px;
  border-left: 3px solid transparent;
}
.nav-item:hover {
  background: var(--color-surface-muted);
}
.nav-item.active {
  background: var(--color-accent-light);
  color: var(--color-accent);
  border-left-color: var(--color-accent);
  font-weight: 500;
}
.nav-item .el-icon {
  font-size: 18px;
}

/* 右侧内容区 */
.main-content {
  flex: 1;
  padding: 32px 40px;
  overflow-y: auto;
}

.page-header {
  margin-bottom: 32px;
}
.page-header h1 {
  font-size: 24px;
  margin-bottom: 8px;
}
.page-header .subtitle {
  color: var(--color-ink-muted);
  font-size: 14px;
}

/* 特性卡片网格 - 严谨直白 */
.features-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 20px;
}

.feature-card {
  display: flex;
  align-items: center;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-base);
  padding: 24px;
  cursor: pointer;
  transition: border-color var(--transition-base), box-shadow var(--transition-base);
}

.feature-card:hover {
  border-color: var(--color-accent);
  box-shadow: var(--shadow-subtle);
}

.card-icon {
  width: 48px;
  height: 48px;
  background: var(--color-accent-light);
  color: var(--color-accent);
  border-radius: var(--radius-base);
  display: flex;
  justify-content: center;
  align-items: center;
  font-size: 24px;
  margin-right: 16px;
}

.card-info {
  flex: 1;
}
.card-info h3 {
  font-size: 15px;
  margin-bottom: 4px;
}
.card-info p {
  font-size: 13px;
  color: var(--color-ink-muted);
}

.arrow {
  color: var(--color-border-hover);
  transition: transform var(--transition-base), color var(--transition-base);
}
.feature-card:hover .arrow {
  color: var(--color-accent);
  transform: translateX(4px);
}
</style>
